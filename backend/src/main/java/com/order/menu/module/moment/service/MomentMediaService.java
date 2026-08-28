package com.order.menu.module.moment.service;

import com.order.menu.common.exception.*;
import com.order.menu.config.UploadProperties;
import com.order.menu.module.moment.dto.MomentDtos;
import java.awt.*; import java.awt.image.BufferedImage; import java.io.*; import java.nio.file.*; import java.util.*; import java.util.List; import java.util.concurrent.Semaphore;
import javax.imageio.*; import javax.imageio.stream.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor
public class MomentMediaService {
  private static final Semaphore SLOTS=new Semaphore(2,true);
  private final UploadProperties properties;
  @Value("${app.moment-media.max-original-bytes:31457280}") private long maxOriginalBytes;
  @Value("${app.moment-media.max-moment-bytes:10485760}") private long maxMomentBytes;
  @Value("${app.moment-media.max-comment-bytes:5242880}") private long maxCommentBytes;
  @Value("${app.moment-media.max-pixels:16000000}") private long maxPixels;
  @Value("${app.moment-media.max-source-pixels:120000000}") private long maxSourcePixels;

  public MomentDtos.UploadResponse store(Long userId,MultipartFile file,boolean comment){
    if(file==null||file.isEmpty())throw bad("请选择图片");if(file.getSize()>maxOriginalBytes)throw bad("图片原文件过大，最大允许30MB");
    boolean acquired=false;
    try{acquired=SLOTS.tryAcquire(30,java.util.concurrent.TimeUnit.SECONDS);if(!acquired)throw bad("图片处理繁忙，请稍后重试");String original=Optional.ofNullable(file.getOriginalFilename()).orElse("");String declared=original.contains(".")?original.substring(original.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";if(!properties.getAllowedExtensions().contains(declared))throw bad("文件扩展名不受支持");byte[] bytes=file.getBytes();Decoded decoded=decode(bytes);if(!sameFormat(declared,decoded.extension))throw bad("文件扩展名与图片内容不一致");long limit=comment?maxCommentBytes:maxMomentBytes;String ext=decoded.extension;
      if(decoded.resized){bytes=jpeg(decoded.image,.9f);ext="jpg";}
      if(bytes.length>=limit){bytes=compress(decoded.image,limit);ext="jpg";}if(bytes.length>=limit)throw bad(comment?"评论图片压缩后仍超过5MB":"动态图片压缩后仍超过10MB");
      Path root=Path.of(properties.getBasePath()).normalize();Path dir=root.resolve(comment?"moment_mes":"moment").resolve(String.valueOf(userId)).normalize();if(!dir.startsWith(root))throw bad("非法图片目录");Files.createDirectories(dir);String filename=UUID.randomUUID().toString().replace("-","")+"."+ext;Files.write(dir.resolve(filename),bytes,StandardOpenOption.CREATE_NEW);
      String url=properties.getImageUrlPrefix()+"/"+(comment?"moment_mes":"moment")+"/"+userId+"/"+filename;String base=properties.getPublicBaseUrl();if(base!=null&&!base.isBlank())url=base.replaceAll("/$","")+url;return new MomentDtos.UploadResponse(url);
    }catch(BusinessException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new BusinessException(ErrorCode.SERVER_ERROR,"图片处理已中断，请重试");}catch(IOException e){throw new BusinessException(ErrorCode.SERVER_ERROR,"图片保存失败");}finally{if(acquired)SLOTS.release();}
  }
  private Decoded decode(byte[] bytes)throws IOException{
    try(ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
      if(in==null)throw bad("文件不是有效图片");Iterator<ImageReader> readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw bad("当前服务不支持该图片编码，请改用 JPG、PNG 或 WebP");ImageReader r=readers.next();
      try{
        r.setInput(in,true,true);String f=r.getFormatName().toLowerCase(Locale.ROOT);if(!List.of("jpg","jpeg","png","webp").contains(f))throw bad("当前服务不支持该图片编码，请改用 JPG、PNG 或 WebP");int w=r.getWidth(0),h=r.getHeight(0);long pixels=(long)w*h;if(w<=0||h<=0||pixels>maxSourcePixels)throw bad("图片分辨率过高，最大允许1.2亿像素");int sample=pixels>maxPixels?(int)Math.ceil(Math.sqrt((double)pixels/maxPixels)):1;ImageReadParam param=r.getDefaultReadParam();if(sample>1)param.setSourceSubsampling(sample,sample,0,0);BufferedImage image=r.read(0,param);if(image==null)throw bad("文件不是有效图片");return new Decoded(image,"jpeg".equals(f)?"jpg":f,sample>1);
      }finally{r.dispose();}
    }
  }
  private byte[] compress(BufferedImage source,long limit)throws IOException{BufferedImage current=source;float quality=.86f;byte[] out=jpeg(current,quality);while(out.length>=limit&&(current.getWidth()>480||current.getHeight()>480)){int w=Math.max(1,(int)(current.getWidth()*.82)),h=Math.max(1,(int)(current.getHeight()*.82));BufferedImage scaled=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);Graphics2D g=scaled.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(current,0,0,w,h,Color.WHITE,null);g.dispose();current=scaled;quality=Math.max(.62f,quality-.03f);out=jpeg(current,quality);}return out;}
  private byte[] jpeg(BufferedImage image,float quality)throws IOException{BufferedImage rgb=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=rgb.createGraphics();g.drawImage(image,0,0,Color.WHITE,null);g.dispose();ImageWriter w=ImageIO.getImageWritersByFormatName("jpeg").next();ByteArrayOutputStream out=new ByteArrayOutputStream();try(ImageOutputStream s=ImageIO.createImageOutputStream(out)){w.setOutput(s);ImageWriteParam p=w.getDefaultWriteParam();p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);p.setCompressionQuality(quality);w.write(null,new IIOImage(rgb,null,null),p);}finally{w.dispose();}return out.toByteArray();}
  private boolean sameFormat(String declared,String detected){return declared.equals(detected)||(List.of("jpg","jpeg").contains(declared)&&"jpg".equals(detected));}
  private BusinessException bad(String m){return new BusinessException(ErrorCode.BAD_REQUEST,m);} private record Decoded(BufferedImage image,String extension,boolean resized){}
}
