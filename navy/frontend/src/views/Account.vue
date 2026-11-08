<template>
 <main class="account-page">
  <header class="account-header"><router-link to="/" class="brand"><Anchor :size="25" /><strong>海战棋</strong></router-link><router-link to="/" class="back"><ArrowLeft :size="16" />返回指挥部</router-link></header>
  <div class="harbor"><img src="/naval/fleet/harbor.png" alt="舰队港口" /></div>
  <section class="account-form" :class="{registering:register}">
   <div class="form-heading"><h1>{{register?'创建舰队账号':'欢迎归航'}}</h1><nav aria-label="账号操作"><router-link to="/login" :class="{active:!register}">登录</router-link><router-link to="/register" :class="{active:register}">注册</router-link></nav></div>
   <Transition name="auth-form" mode="out-in" appear>
    <form :key="register?'register':'login'" @submit.prevent="submit" :class="{registration:register}">
     <label>{{register?'指挥官昵称':'邮箱或指挥官昵称'}}<AppInput v-model="nickname" :aria-label="register?'指挥官昵称':'邮箱或指挥官昵称'" autocomplete="username" :placeholder="register?'您的指挥官昵称':'请输入邮箱或指挥官昵称'" required :maxlength="register?32:254"><template #icon><UserRound :size="17" /></template></AppInput></label>
     <label v-if="register">邮箱<AppInput v-model="email" aria-label="邮箱" type="email" autocomplete="email" placeholder="请输入邮箱" required><template #icon><Mail :size="17" /></template></AppInput></label>
     <label>密码<AppInput v-model="password" aria-label="密码" type="password" :autocomplete="register?'new-password':'current-password'" placeholder="请输入密码" minlength="8" maxlength="72" required><template #icon><LockKeyhole :size="17" /></template></AppInput></label>
     <label v-if="register">确认密码<AppInput v-model="confirmPassword" aria-label="确认密码" type="password" autocomplete="new-password" placeholder="请再次输入密码" minlength="8" maxlength="72" required><template #icon><LockKeyhole :size="17" /></template></AppInput></label>
     <label class="wide">图形验证码<span class="verification-row"><AppInput v-model="captcha" aria-label="图形验证码" placeholder="输入右侧四位字符" maxlength="4" minlength="4" autocomplete="off" required><template #icon><ShieldCheck :size="17" /></template></AppInput><button type="button" class="captcha" title="刷新验证码" aria-label="刷新验证码" @click="refreshCaptcha"><img v-if="challenge.image" :src="challenge.image" alt="四位图形验证码" /><RefreshCw v-else :size="20" /></button></span></label>
     <label v-if="register" class="wide">邮箱验证码<span class="verification-row"><AppInput v-model="emailCode" aria-label="邮箱验证码" placeholder="六位邮箱验证码" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required /><button type="button" class="send" :disabled="sending||cooldown>0||captcha.length!==4||!email" @click="sendCode">{{cooldown>0?cooldown+' 秒后重试':sending?'发送中...':'发送验证码'}}</button></span><small>支持 QQ、163、126、Gmail 邮箱</small></label>
     <Transition name="feedback"><p v-if="error" class="error wide" role="alert">{{error}}</p><p v-else-if="sent" class="success wide" role="status">验证码已发送，请查收邮箱</p></Transition>
     <button class="primary wide" :disabled="busy"><LogIn :size="18" />{{busy?'连接中...':register?'注册并进入':'登录'}}</button>
    </form>
   </Transition>
  </section>
 </main>
</template>
<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Anchor, ArrowLeft, LogIn, RefreshCw, UserRound, LockKeyhole, Mail, ShieldCheck } from 'lucide-vue-next'
import client from '../api/client'
import AppInput from '../components/AppInput.vue'
const route=useRoute(),router=useRouter(),register=computed(()=>route.path==='/register')
const nickname=ref(''),password=ref(''),confirmPassword=ref(''),email=ref(''),emailCode=ref(''),captcha=ref(''),challenge=ref({}),busy=ref(false),sending=ref(false),error=ref(''),sent=ref(false),cooldown=ref(0)
let timer,refreshTimer,disposed=false,captchaRetries=0
async function refreshCaptcha(){try{challenge.value=await client.get('/auth/captcha');captcha.value='';captchaRetries=0}catch(e){if(e.code===429&&captchaRetries++<3&&!disposed){clearTimeout(refreshTimer);refreshTimer=setTimeout(refreshCaptcha,1200)}else error.value=e.message}}
async function sendCode(){error.value='';sent.value=false;sending.value=true;try{await client.post('/auth/email-code',{email:email.value,captchaId:challenge.value.id,captcha:captcha.value});sent.value=true;cooldown.value=60}catch(e){error.value=e.message}finally{sending.value=false;await refreshCaptcha()}}
async function submit(){
 if(register.value&&password.value!==confirmPassword.value){error.value='两次输入的密码不一致';return}
 busy.value=true;error.value=''
 try{const result=await client.post(`/auth/${register.value?'register':'login'}`,{nickname:nickname.value.trim(),password:password.value,confirmPassword:confirmPassword.value,email:email.value,emailCode:emailCode.value,captchaId:challenge.value.id,captcha:captcha.value});localStorage.setItem('navy_token',result.token);localStorage.setItem('navy_user',JSON.stringify(result.user));const target=route.query.redirect;await router.push(typeof target==='string'&&target.startsWith('/')&&!target.startsWith('//')?target:'/')}
 catch(e){error.value=e.message;await refreshCaptcha()}finally{busy.value=false}
}
onMounted(()=>{refreshCaptcha();timer=setInterval(()=>{if(cooldown.value>0)cooldown.value--},1000)})
onUnmounted(()=>{disposed=true;clearInterval(timer);clearTimeout(refreshTimer)})
watch(register,()=>{error.value='';sent.value=false;refreshTimer=setTimeout(refreshCaptcha,1100)})
</script>
<style scoped>
.account-page{min-height:100dvh;padding:0 24px 40px;background:#f4f8f8;color:#416977}.account-header{height:76px;max-width:1040px;margin:auto;display:flex;align-items:center;justify-content:space-between;gap:16px}.brand,.back{display:flex;align-items:center;gap:9px;color:#426c7b;white-space:nowrap}.brand strong{font-size:21px}.brand svg{color:#ef8c2e}.back{font-size:12px;color:#7b96a0}.harbor{height:140px;max-width:1040px;margin:auto;overflow:hidden;border-radius:5px;background:#cfdee3}.harbor img{width:100%;height:100%;object-fit:cover;object-position:center 52%;image-rendering:auto}.account-form{width:100%;max-width:400px;margin:34px auto 0;transition:max-width .3s ease}.account-form.registering{max-width:560px}.form-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:26px;padding-bottom:17px;border-bottom:1px solid #dce6e9}h1{font-size:22px;font-weight:650;line-height:1.4;white-space:nowrap}.form-heading nav{display:flex;gap:20px;flex-shrink:0;font-size:13px}.form-heading nav a{padding:8px 0;position:relative;color:#8ba1aa;transition:color .2s}.form-heading nav a:after{content:'';position:absolute;left:0;right:0;bottom:0;height:2px;background:#da9b51;transform:scaleX(0);transition:transform .25s}.form-heading nav a.active{color:#426e7e}.form-heading nav a.active:after{transform:scaleX(1)}form{display:grid;grid-template-columns:minmax(0,1fr);gap:19px 18px}.registration{grid-template-columns:repeat(2,minmax(0,1fr))}label{display:flex;flex-direction:column;gap:8px;font-size:12px;color:#65828e;min-width:0}.wide{grid-column:1/-1}.verification-row{display:grid;grid-template-columns:minmax(0,1fr) 144px;gap:12px;min-width:0}.verification-row>button{height:44px;min-width:0;border-radius:5px;display:flex;align-items:center;justify-content:center;transition:background .2s,border-color .2s;white-space:nowrap;font-size:12px}.captcha{background:#eaf3f5;border:1px solid #d0dfe4;overflow:hidden}.captcha img{width:100%;height:44px;object-fit:fill}.send{color:#567c8b;background:#e7eff2;border:1px solid #cfdee3}.send:hover:not(:disabled){background:#dce9ee}.primary{height:44px;display:flex;align-items:center;justify-content:center;gap:8px;margin-top:5px;border-radius:5px;background:#ee8d32;color:#fff;font-size:14px;transition:background .2s,transform .2s;white-space:nowrap}.primary:hover:not(:disabled){background:#dc7e26}.primary:active:not(:disabled){transform:translateY(1px)}small{font-size:10px;color:#94a8b0;line-height:1.5;overflow-wrap:anywhere}.error,.success{font-size:12px;line-height:1.6;overflow-wrap:anywhere}.error{color:#b9584b}.success{color:#43806c}.auth-form-enter-active,.auth-form-leave-active{transition:opacity .22s ease,transform .26s ease}.auth-form-enter-from{opacity:0;transform:translateY(9px)}.auth-form-leave-to{opacity:0;transform:translateY(-7px)}.feedback-enter-active,.feedback-leave-active{transition:opacity .2s,transform .2s}.feedback-enter-from,.feedback-leave-to{opacity:0;transform:translateY(4px)}@media(max-width:600px){.account-page{padding:0 20px 32px}.account-header{height:64px}.brand strong{font-size:19px}.harbor{height:116px}.account-form{margin-top:28px}.account-form.registering{max-width:400px}.registration{grid-template-columns:minmax(0,1fr)}.form-heading{gap:12px;margin-bottom:22px}h1{font-size:20px}.verification-row{grid-template-columns:minmax(0,1fr) 120px;gap:10px}.form-heading nav{gap:18px}form{gap:17px}}@media(prefers-reduced-motion:reduce){*{transition-duration:.01s!important}}
</style>
