package com.navy.identity;

import com.navy.common.exception.BusinessException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationService {
    private final JavaMailSender mail;
    @Value("${spring.mail.username}") private String sender;
    @Value("${app.mail.sender-name:海战棋}") private String senderName="海战棋";
    private final SecureRandom random=new SecureRandom();
    private record Code(String hash,long expiry,int attempts) {}
    private final Map<String,Code> captchas=new ConcurrentHashMap<>(), emails=new ConcurrentHashMap<>();
    private final Map<String,Long> limits=new ConcurrentHashMap<>();
    private synchronized void limit(String key,long delay){long now=System.currentTimeMillis();Long until=limits.get(key);if(until!=null&&until>now)throw new BusinessException(429,"操作过于频繁，请稍后重试");limits.put(key,now+delay);}
    public synchronized Map<String,String> captcha(String ip) {
        cleanup();limit("image:"+ip,1000);if(captchas.size()>10000)throw new BusinessException(429,"请稍后重试");
        String alphabet="23456789ABCDEFGHJKLMNPQRSTUVWXYZ",text="";
        for(int i=0;i<4;i++)text+=alphabet.charAt(random.nextInt(alphabet.length()));
        String id=UUID.randomUUID().toString();captchas.put(id,new Code(GuestService.hash(text),System.currentTimeMillis()+180000,0));
        try {
            BufferedImage image=new BufferedImage(144,56,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(new Color(235,244,246));g.fillRect(0,0,144,56);
            g.setFont(new Font(Font.MONOSPACED,Font.BOLD,29));
            for(int i=0;i<4;i++){g.setColor(new Color(40+random.nextInt(80),90+random.nextInt(50),100+random.nextInt(50)));g.drawString(text.substring(i,i+1),17+i*30,36+random.nextInt(8));}
            for(int i=0;i<9;i++){g.setColor(new Color(125,168,176));g.drawLine(random.nextInt(144),random.nextInt(56),random.nextInt(144),random.nextInt(56));}g.dispose();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
            return Map.of("id",id,"image","data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray()));
        }catch(Exception e){throw new IllegalStateException("Captcha rendering failed",e);}
    }
    public synchronized void captcha(String id,String answer,String ip) {
        limit("attempt:"+ip,500);Code code=id==null?null:captchas.remove(id);
        if(code==null||code.expiry()<System.currentTimeMillis()||answer==null||!code.hash().equals(GuestService.hash(answer.trim().toUpperCase(Locale.ROOT))))throw new BusinessException(400,"图形验证码错误或已过期，请刷新");
    }
    public static String email(String raw){String value=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);if(value.length()>254||!value.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(qq\\.com|163\\.com|126\\.com|gmail\\.com)"))throw new BusinessException(400,"仅支持 qq.com、163.com、126.com、gmail.com 邮箱");return value;}
    public void send(String raw,String id,String answer,String ip) {
        send(raw,id,answer,ip,"register");
    }
    public void send(String raw,String id,String answer,String ip,String purpose) {
        captcha(id,answer,ip);String email=email(raw);limit("mail-ip:"+ip,60000);limit("mail:"+email,60000);
        String text=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        try{
            String name=senderName==null||senderName.isBlank()?"海战棋":senderName.strip();
            if(senderName!=null&&senderName.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid mail sender display name");
            var content=VerificationEmail.render(text,purpose.startsWith("bind:"));
            var message=mail.createMimeMessage();
            var from=new InternetAddress(sender,name,StandardCharsets.UTF_8.name());from.validate();
            message.setFrom(from);message.setRecipient(Message.RecipientType.TO,new InternetAddress(email));
            message.setSubject(content.subject(),StandardCharsets.UTF_8.name());
            var plain=new MimeBodyPart();plain.setText(content.plainText(),StandardCharsets.UTF_8.name());
            var html=new MimeBodyPart();html.setText(content.html(),StandardCharsets.UTF_8.name(),"html");
            var alternatives=new MimeMultipart("alternative");alternatives.addBodyPart(plain);alternatives.addBodyPart(html);message.setContent(alternatives);
            mail.send(message);
        }catch(Exception e){throw new BusinessException(503,"邮件暂时无法发送，请稍后重试");}
        emails.put(purpose+":"+email,new Code(GuestService.hash(text),System.currentTimeMillis()+300000,0));
    }
    public synchronized void verifyEmail(String email,String answer) {
        verifyEmail(email,answer,"register");
    }
    public synchronized void verifyEmail(String email,String answer,String purpose) {
        String key=purpose+":"+email;
        Code code=emails.get(key);
        if(code==null||code.expiry()<System.currentTimeMillis()||code.attempts()>=5)throw new BusinessException(400,"邮箱验证码已失效，请重新获取");
        if(answer==null||!code.hash().equals(GuestService.hash(answer))){emails.put(key,new Code(code.hash(),code.expiry(),code.attempts()+1));throw new BusinessException(400,"邮箱验证码错误");}
        emails.remove(key);
    }
    @Scheduled(fixedDelay=60000) public void cleanup(){long now=System.currentTimeMillis();captchas.values().removeIf(c->c.expiry()<now);emails.values().removeIf(c->c.expiry()<now);limits.values().removeIf(t->t<now);}
}
