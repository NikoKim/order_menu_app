package com.navy.identity;

import com.navy.common.exception.BusinessException;
import com.navy.common.response.ApiResponse;
import com.navy.security.UserContext;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/account")
public class AvatarController {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AvatarController.class);
    private static final String AVATAR_PREFIX="/api/account/avatars/";
    private final JdbcTemplate jdbc;
    private final AccountController accounts;
    private final Path directory;

    public AvatarController(JdbcTemplate jdbc,AccountController accounts,
            @Value("${app.avatar-directory}") String directory) {
        this.jdbc=jdbc;this.accounts=accounts;this.directory=Path.of(directory).toAbsolutePath().normalize();
    }

    @PutMapping(value="/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file) {
        long id=UserContext.get().userId();
        if(file.isEmpty()||file.getSize()>2*1024*1024)throw new BusinessException(400,"请选择不超过2MB的 PNG 或 JPG 图片");
        BufferedImage image=decode(file);
        String name=UUID.randomUUID()+".png";
        Path target=directory.resolve(name);
        try {
            Files.createDirectories(directory);
            if(!ImageIO.write(image,"png",target.toFile()))throw new IOException("PNG writer unavailable");
        }catch(IOException e){try{Files.deleteIfExists(target);}catch(IOException ignored){}throw new BusinessException(503,"头像暂时无法保存，请稍后重试");}
        try {
            String previous;
            try { previous=jdbc.queryForObject("SELECT avatar_url FROM users WHERE id=? FOR UPDATE",String.class,id); }
            catch(EmptyResultDataAccessException e){throw new BusinessException(401,"账号不存在，请重新登录");}
            if(jdbc.update("UPDATE users SET avatar_url=? WHERE id=?",AVATAR_PREFIX+name,id)!=1)throw new BusinessException(401,"账号不存在，请重新登录");
            boolean synchronizedTransaction=TransactionSynchronizationManager.isSynchronizationActive();
            if(synchronizedTransaction) {
                // The row lock serializes replacements; never delete its old file before commit.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit(){deletePrevious(previous,target);}
                    @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)deleteFile(target);}
                });
            }
            var profile=accounts.profile(id);
            if(!synchronizedTransaction)deletePrevious(previous,target);
            return ApiResponse.success(profile);
        }catch(RuntimeException e){try{Files.deleteIfExists(target);}catch(IOException ignored){}throw e;}
    }

    private void deletePrevious(String previous,Path replacement) {
        if(previous==null||!previous.startsWith(AVATAR_PREFIX))return;
        String name=previous.substring(AVATAR_PREFIX.length());
        if(!name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png"))return;
        Path old=directory.resolve(name).normalize();
        if(old.getParent().equals(directory)&&!old.equals(replacement)&&Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS))deleteFile(old);
    }

    private void deleteFile(Path path) {
        try { Files.deleteIfExists(path); }
        catch(IOException e){LOG.warn("Unable to remove replaced avatar {}",path.getFileName(),e);}
    }

    private BufferedImage decode(MultipartFile file) {
        try(var source=file.getInputStream();var input=ImageIO.createImageInputStream(source)) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new BusinessException(400,"图片无法读取，请选择 PNG 或 JPG 图片");
            var reader=readers.next();
            try {
                if(!Set.of("png","jpeg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT)))throw new BusinessException(400,"仅支持 PNG 或 JPG 图片");
                reader.setInput(input,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>4096||h>4096||(long)w*h>4_000_000)throw new BusinessException(400,"图片尺寸过大，请缩小后上传");
                var original=reader.read(0);int side=Math.min(w,h),size=Math.min(side,512);
                var result=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
                var graphics=result.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int x=(w-side)/2,y=(h-side)/2;
                    graphics.drawImage(original,0,0,size,size,x,y,x+side,y+side,null);
                }finally{graphics.dispose();}
                return result;
            }finally{reader.dispose();}
        }catch(IOException e){throw new BusinessException(400,"图片无法读取，请更换图片");}
    }

    @GetMapping("/avatars/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        if(!name.matches("[a-f0-9-]{36}\\.png"))return ResponseEntity.notFound().build();
        var file=new FileSystemResource(directory.resolve(name));
        if(!file.isReadable())return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .header("X-Content-Type-Options","nosniff")
            .cacheControl(CacheControl.maxAge(365,TimeUnit.DAYS).cachePublic().immutable()).body(file);
    }
}
