package com.navy.identity;

/** Self-contained transactional mail, with no remote fonts, images or tracking. */
final class VerificationEmail {
    private VerificationEmail() {}
    record Content(String subject,String plainText,String html) {}

    static Content render(String code,boolean binding) {
        if(code==null||!code.matches("[0-9]{6}"))throw new IllegalArgumentException("A six-digit verification code is required");
        String action=binding?"绑定邮箱":"注册";
        String title=binding?"确认绑定邮箱":"完成账户注册";
        String lead=binding?"你正在为海战棋账户绑定邮箱。请在绑定页面输入以下验证码，确认这次操作。":"欢迎加入海战棋。请在注册页面输入以下验证码，完成账户注册。";
        String plain="海战棋 · "+action+"验证\n\n"+lead+"\n\n验证码："+code+"\n5分钟内有效。\n\n请勿向任何人透露此验证码。海战棋不会通过聊天、电话或邮件向你索取验证码。\n如果这不是你的操作，请忽略这封邮件，无需回复。\n";
        String html="""
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="color-scheme" content="light only">
              <meta name="supported-color-schemes" content="light">
              <title>{{title}} · 海战棋</title>
            </head>
            <body style="margin:0;padding:0;background-color:#edf5f7;color:#315764;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei','PingFang SC',Arial,sans-serif;letter-spacing:0;color-scheme:light;">
              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" bgcolor="#edf5f7" style="width:100%;background-color:#edf5f7;">
                <tr><td align="center" style="padding:32px 16px;">
                  <table role="presentation" width="560" cellspacing="0" cellpadding="0" border="0" bgcolor="#ffffff" style="width:100%;max-width:560px;background-color:#ffffff;border-collapse:collapse;">
                    <tr><td height="4" bgcolor="#d99036" style="height:4px;background-color:#d99036;font-size:0;line-height:0;">&nbsp;</td></tr>
                    <tr><td style="padding:28px 28px 22px;">
                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;border-collapse:collapse;">
                        <tr>
                          <td style="font-size:22px;line-height:30px;font-weight:700;color:#245463;letter-spacing:0;">海战棋</td>
                          <td align="right" style="font-size:12px;line-height:20px;color:#7b939a;white-space:nowrap;">账户验证</td>
                        </tr>
                      </table>
                    </td></tr>
                    <tr><td style="padding:0 28px;"><table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"><tr><td height="1" bgcolor="#e4edef" style="height:1px;background-color:#e4edef;font-size:0;line-height:0;">&nbsp;</td></tr></table></td></tr>
                    <tr><td style="padding:26px 28px 22px;">
                      <h1 style="margin:0 0 12px;font-size:24px;line-height:34px;font-weight:700;color:#245463;letter-spacing:0;">{{title}}</h1>
                      <p style="margin:0;font-size:14px;line-height:25px;color:#5d7a84;">{{lead}}</p>
                    </td></tr>
                    <tr><td style="padding:0 28px 24px;">
                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" bgcolor="#fff6e8" style="width:100%;background-color:#fff6e8;border-left:3px solid #d99036;">
                        <tr><td style="padding:18px 22px 20px;">
                          <p style="margin:0 0 8px;font-size:12px;line-height:20px;color:#867052;">{{action}}验证码</p>
                          <p style="margin:0 0 10px;font-family:'SFMono-Regular',Consolas,'Liberation Mono',monospace;font-size:40px;line-height:48px;font-weight:700;font-variant-numeric:tabular-nums;letter-spacing:0;color:#ad6214;white-space:nowrap;">{{code}}</p>
                          <p style="margin:0;font-size:12px;line-height:20px;color:#867052;">5分钟内有效</p>
                        </td></tr>
                      </table>
                    </td></tr>
                    <tr><td style="padding:0 28px 26px;">
                      <p style="margin:0 0 6px;font-size:13px;line-height:23px;font-weight:700;color:#315764;">请勿向任何人透露验证码</p>
                      <p style="margin:0;font-size:13px;line-height:23px;color:#6a838c;">海战棋不会通过聊天、电话或邮件向你索取验证码。</p>
                    </td></tr>
                    <tr><td bgcolor="#f7fafb" style="padding:18px 28px;background-color:#f7fafb;border-top:1px solid #e4edef;">
                      <p style="margin:0;font-size:12px;line-height:22px;color:#7b939a;">如果这不是你的操作，请忽略这封邮件，无需回复。</p>
                    </td></tr>
                  </table>
                  <p style="margin:18px 0 0;font-size:12px;line-height:20px;color:#7b939a;">海战棋 · 账户安全</p>
                </td></tr>
              </table>
            </body>
            </html>
            """;
        html=html.replace("{{title}}",title).replace("{{lead}}",lead).replace("{{action}}",action).replace("{{code}}",code);
        return new Content("海战棋"+action+"验证码",plain,html);
    }
}
