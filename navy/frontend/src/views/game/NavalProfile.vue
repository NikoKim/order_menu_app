<template>
  <AppScrollArea tag="section" class="naval-profile community-body" aria-label="个人主页" :aria-busy="loading">
    <AppSkeleton v-if="loading" variant="profile" :rows="4" label="正在加载个人信息" />
    <template v-else-if="profile">
      <section class="profile-info" aria-label="用户个人信息">
        <div class="profile-heading">
          <button class="profile-avatar" type="button" title="修改头像" aria-label="修改头像" :disabled="uploading || decoding" @click="avatarInput.click()">
            <img v-if="profile.avatarUrl && !avatarFailed" :src="backendAssetUrl(profile.avatarUrl)" alt="我的头像" @error="avatarFailed=true" />
            <UserRound v-else :size="32" /><span><LoaderCircle v-if="uploading || decoding" class="spinning" :size="13" /><Camera v-else :size="13" /></span>
          </button>
          <input ref="avatarInput" class="sr-only" type="file" accept="image/png,image/jpeg" aria-label="选择头像图片" :disabled="uploading || decoding" @change="chooseAvatar" />
          <div class="profile-name"><h3>{{ profile.nickname }}</h3></div>
          <button class="profile-settings-toggle" type="button" title="账户设置" aria-label="账户设置" :aria-expanded="settingsOpen" aria-controls="profile-account-settings" @click="settingsOpen=!settingsOpen"><Settings2 :size="19" /></button>
        </div>
        <div class="profile-spectator-preference"><label><Eye :size="15" /><span>默认开启观战</span><small>新对局生效</small><LoaderCircle v-if="spectatorSaving" :size="13" class="spinning" /><input type="checkbox" role="switch" :checked="profile.defaultAllowSpectators !== false" :disabled="spectatorSaving" aria-label="默认开启观战" @change="saveSpectatorPreference" /></label><p v-if="spectatorError" role="alert">{{ spectatorError }}</p></div>
        <Transition name="profile-feedback" @after-leave="clearClosedPreview"><section v-if="previewOpen" class="avatar-inline-preview" aria-label="头像预览">
          <header class="inline-preview-heading"><h4><Camera :size="16" />预览头像</h4><button class="preview-close" type="button" title="取消头像预览" aria-label="取消头像预览" :disabled="uploading" @click="closePreview"><X :size="16" /></button></header>
          <div class="avatar-preview-stage"><img v-if="previewUrl" class="avatar-preview-square" :src="previewUrl" alt="新头像方形预览" /><div class="avatar-preview-identity"><img v-if="previewUrl" :src="previewUrl" alt="新头像圆形预览" /><strong>{{ profile.nickname }}</strong></div></div>
          <footer class="preview-actions"><button type="button" class="profile-button reselect-avatar" :disabled="uploading || decoding" @click="avatarInput.click()"><Camera :size="15" />重新选择</button><button type="button" class="profile-button" :disabled="uploading" @click="closePreview">取消</button><button type="button" class="profile-button primary" :disabled="uploading || decoding || !selectedFile" @click="uploadAvatar"><LoaderCircle v-if="uploading" class="spinning" :size="15" /><Check v-else :size="15" />{{ uploading ? '保存中...' : '确认更换' }}</button></footer>
        </section></Transition>
        <Transition name="profile-feedback"><div v-if="settingsOpen" id="profile-account-settings" class="profile-account-settings">
        <dl class="profile-details"><dt><Mail :size="15" />邮箱</dt><dd><span>{{ profile.email || '尚未绑定' }}</span><button v-if="!profile.email" class="email-bind" type="button" :aria-expanded="binding" @click="beginBinding"><MailPlus :size="13" />绑定邮箱</button></dd></dl>
        <Transition name="profile-feedback"><form v-if="binding && !profile.email" class="binding-form" @submit.prevent="bindEmail">
          <h4>绑定邮箱</h4>
          <label>邮箱<AppInput v-model="email" aria-label="绑定邮箱地址" type="email" autocomplete="email" placeholder="请输入邮箱" maxlength="254" required><template #icon><Mail :size="16" /></template></AppInput></label>
          <small>支持 QQ、163、126、Gmail 邮箱</small>
          <label>图形验证码<span class="verification-row"><AppInput v-model="captcha" aria-label="绑定图形验证码" placeholder="四位字符" maxlength="4" autocomplete="off" /><button class="captcha-image" type="button" title="刷新验证码" aria-label="刷新绑定验证码" :disabled="refreshing" @click="refreshCaptcha"><img v-if="challenge.image" :src="challenge.image" alt="四位图形验证码" /><RefreshCw v-else :size="18" /></button></span></label>
          <label>邮箱验证码<span class="verification-row"><AppInput v-model="emailCode" aria-label="绑定邮箱验证码" placeholder="六位数字" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required /><button class="profile-button" type="button" :disabled="sending || cooldown>0 || captcha.trim().length!==4 || !email.trim()" @click="sendCode">{{ cooldown>0 ? `${cooldown}秒后重试` : sending ? '发送中...' : '发送验证码' }}</button></span></label>
          <div class="binding-actions"><button class="profile-button primary" :disabled="saving || sending || !/^[0-9]{6}$/.test(emailCode)"><LoaderCircle v-if="saving" class="spinning" :size="15" /><Check v-else :size="15" />确认绑定</button><button class="profile-button" type="button" :disabled="saving || sending" @click="binding=false">取消</button></div>
        </form></Transition>
        <div class="profile-session"><button type="button" class="profile-logout" :disabled="uploading" @click="logoutOpen=true">退出登录</button></div>
        </div></Transition>
      </section>
    </template>
    <button v-if="!profile && !loading" class="profile-button" type="button" @click="load"><RefreshCw :size="14" />重新读取个人信息</button>
    <template v-if="profile">
      <nav ref="profileTabs" class="profile-tabs" role="tablist" aria-label="个人主页视图" @keydown="onTabKeydown"><button id="profile-overview-tab" type="button" role="tab" :aria-selected="profileTab==='overview'" :tabindex="profileTab==='overview'?0:-1" aria-controls="profile-overview-panel" @click="profileTab='overview'">概览</button><button id="profile-inventory-tab" type="button" role="tab" :aria-selected="profileTab==='inventory'" :tabindex="profileTab==='inventory'?0:-1" aria-controls="profile-inventory-panel" @click="profileTab='inventory'">我的物品</button></nav>
      <div v-if="profileTab==='overview'" id="profile-overview-panel" role="tabpanel" aria-labelledby="profile-overview-tab">
        <NavalProgression />
        <section class="profile-medals" aria-label="我的勋章墙"><header class="profile-section-heading"><h3><Medal :size="17" />勋章墙</h3></header><NavalMedalWall :person="self" embedded /></section>
      </div>
      <div v-else id="profile-inventory-panel" class="profile-inventory" role="tabpanel" aria-labelledby="profile-inventory-tab"><NavalShop initial-tab="inventory" embedded /></div>
    </template>
  </AppScrollArea>
  <AppDialog :open="logoutOpen" title="退出登录" :dismissible="!loggingOut" @close="logoutOpen=false"><h2 class="preview-heading">退出登录</h2><p class="logout-question">确定退出当前账号？</p><footer class="logout-actions"><button type="button" class="profile-button" :disabled="loggingOut" @click="logoutOpen=false">取消</button><button type="button" class="profile-button primary" :disabled="loggingOut" @click="confirmLogout">{{ loggingOut ? '正在退出...' : '退出登录' }}</button></footer></AppDialog>
</template>

<script setup>
import {nextTick,onMounted,onUnmounted,ref,watch} from 'vue'
import {Camera,Check,Eye,LoaderCircle,Mail,MailPlus,Medal,RefreshCw,Settings2,UserRound,X} from 'lucide-vue-next'
import AppInput from '../../components/AppInput.vue'
import AppSkeleton from '../../components/AppSkeleton.vue'
import AppScrollArea from '../../components/AppScrollArea.vue'
import AppDialog from '../../components/AppDialog.vue'
import client from '../../api/client'
import {backendAssetUrl} from '../../config/endpoints'
import NavalMedalWall from './NavalMedalWall.vue'
import NavalProgression from './NavalProgression.vue'
import NavalShop from './NavalShop.vue'
import {notifyError,notifySuccess} from '../../services/notice'
import {saveAccountSettings} from '../../api/account'

const emit=defineEmits(['updated','logout'])
const self={self:true},profile=ref(null),loading=ref(true),error=ref(''),success=ref('')
const avatarInput=ref(null),uploading=ref(false),avatarFailed=ref(false)
const previewOpen=ref(false),previewUrl=ref(''),selectedFile=ref(null),previewError=ref(''),decoding=ref(false)
const logoutOpen=ref(false),loggingOut=ref(false)
const settingsOpen=ref(false)
const spectatorSaving=ref(false),spectatorError=ref('')
for(const source of [error,previewError])watch(source,value=>{if(value)notifyError(value)},{flush:'sync'})
watch(success,value=>{if(value)notifySuccess(value)},{flush:'sync'})
const profileTab=ref('overview'),profileTabs=ref(null)
const binding=ref(false),email=ref(''),emailCode=ref(''),captcha=ref(''),challenge=ref({}),refreshing=ref(false),sending=ref(false),saving=ref(false),cooldown=ref(0)
let timer,disposed=false,selectionVersion=0,profileRequest=0,settingsRequest=0,uploadRequest=0,emailRequest=0,profileAccount=''
function update(value,accountToken){if(disposed||accountToken!==profileAccount||accountToken!==localStorage.getItem('navy_token')||profile.value?.id!=null&&value?.id!==profile.value.id)return false;profile.value=value;emit('updated',value);return true}
async function load(){
 const request=++profileRequest,token=localStorage.getItem('navy_token')
 settingsRequest++;uploadRequest++;emailRequest++;selectionVersion++;spectatorSaving.value=false;spectatorError.value='';uploading.value=false;saving.value=false;decoding.value=false;binding.value=false;previewOpen.value=false;clearPreview();profile.value=null;profileAccount='';loading.value=true;error.value=''
 if(!token){loading.value=false;return}
 try{const value=await client.get('/account');if(!disposed&&request===profileRequest&&token===localStorage.getItem('navy_token')){profileAccount=token;update(value,token)}}catch(e){if(!disposed&&request===profileRequest&&token===localStorage.getItem('navy_token'))error.value=e.message}finally{if(!disposed&&request===profileRequest)loading.value=false}
}
async function saveSpectatorPreference(event){
 const token=localStorage.getItem('navy_token'),current=profile.value,desired=event.target.checked
 event.target.checked=current?.defaultAllowSpectators!==false
 if(!token||!current||spectatorSaving.value||desired===(current.defaultAllowSpectators!==false))return
 if(token!==profileAccount){load();return}
 const request=++settingsRequest
 spectatorSaving.value=true;spectatorError.value=''
 try{
  const settings=await saveAccountSettings({defaultAllowSpectators:desired})
  if(!disposed&&request===settingsRequest&&token===localStorage.getItem('navy_token')&&profile.value?.id===current.id)update({...profile.value,...settings},token)
 }catch(e){if(!disposed&&request===settingsRequest&&token===localStorage.getItem('navy_token')){spectatorError.value=e.message||'保存观战设置失败';notifyError(spectatorError.value)}}finally{if(!disposed&&request===settingsRequest)spectatorSaving.value=false}
}
function clearPreview(){if(previewUrl.value)URL.revokeObjectURL(previewUrl.value);previewUrl.value='';selectedFile.value=null;previewError.value=''}
function clearClosedPreview(){if(!previewOpen.value)clearPreview()}
function closePreview(){if(uploading.value)return;selectionVersion++;decoding.value=false;previewOpen.value=false}
function confirmLogout(){if(loggingOut.value)return;loggingOut.value=true;emit('logout')}
function onTabKeydown(event){if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();profileTab.value=event.key==='Home'?'overview':event.key==='End'?'inventory':profileTab.value==='overview'?'inventory':'overview';nextTick(()=>profileTabs.value?.querySelector('[aria-selected="true"]')?.focus())}
async function chooseAvatar(event){
  const file=event.target.files?.[0];event.target.value='';if(!file)return
  const revision=++selectionVersion
  error.value='';success.value='';previewError.value=''
  if(!['image/png','image/jpeg'].includes(file.type)||file.size>2*1024*1024){(previewOpen.value?previewError:error).value='请选择不超过2MB的 PNG 或 JPG 图片';return}
  decoding.value=true
  let image
  try{
    image=await createImageBitmap(file)
    const {width,height}=image
    if(width<1||height<1||width>4096||height>4096||width*height>4_000_000)throw new Error('图片尺寸过大，请缩小后上传')
    const side=Math.min(width,height),size=Math.min(side,512),canvas=document.createElement('canvas')
    canvas.width=size;canvas.height=size
    const context=canvas.getContext('2d')
    if(!context)throw new Error('图片无法读取，请更换图片')
    context.imageSmoothingQuality='high'
    context.drawImage(image,(width-side)/2,(height-side)/2,side,side,0,0,size,size)
    const blob=await new Promise((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(new Error('图片无法读取，请更换图片')),'image/png'))
    if(disposed||revision!==selectionVersion)return
    clearPreview()
    selectedFile.value=new File([blob],'avatar.png',{type:'image/png'})
    previewUrl.value=URL.createObjectURL(blob);previewOpen.value=true
  }catch(e){if(!disposed&&revision===selectionVersion)(previewOpen.value?previewError:error).value=e.message?.includes('尺寸')?e.message:'图片无法读取，请更换图片'}
  finally{image?.close();if(revision===selectionVersion)decoding.value=false}
}
async function uploadAvatar(){
  if(uploading.value||!selectedFile.value)return
  const request=++uploadRequest,token=profileAccount,id=profile.value?.id
  if(!token||token!==localStorage.getItem('navy_token')){load();return}
  const current=()=>!disposed&&request===uploadRequest&&token===profileAccount&&token===localStorage.getItem('navy_token')&&profile.value?.id===id
  previewError.value=''
  uploading.value=true
  try{const form=new FormData();form.append('file',selectedFile.value);const value=await client.put('/account/avatar',form,{headers:{'Content-Type':'multipart/form-data'}});if(!current())return;update(value,token);avatarFailed.value=false;success.value='头像已更新';previewOpen.value=false}catch(e){if(current())previewError.value=e.message}finally{if(!disposed&&request===uploadRequest)uploading.value=false}
}
async function refreshCaptcha(){refreshing.value=true;try{challenge.value=await client.get('/auth/captcha');captcha.value=''}catch(e){error.value=e.message}finally{refreshing.value=false}}
function beginBinding(){binding.value=true;error.value='';success.value='';if(!challenge.value.id)refreshCaptcha()}
async function sendCode(){
  sending.value=true;error.value='';success.value=''
  try{await client.post('/account/email-code',{email:email.value,captchaId:challenge.value.id,captcha:captcha.value});if(disposed)return;success.value='验证码已发送，请查收邮箱';cooldown.value=60;clearInterval(timer);timer=setInterval(()=>{cooldown.value=Math.max(0,cooldown.value-1);if(!cooldown.value)clearInterval(timer)},1000)}catch(e){error.value=e.message}finally{sending.value=false;if(!disposed)await refreshCaptcha()}
}
async function bindEmail(){
 const request=++emailRequest,token=profileAccount,id=profile.value?.id
 if(!token||token!==localStorage.getItem('navy_token')){load();return}
 const current=()=>!disposed&&request===emailRequest&&token===profileAccount&&token===localStorage.getItem('navy_token')&&profile.value?.id===id
 saving.value=true;error.value='';success.value=''
 try{const value=await client.put('/account/email',{email:email.value,emailCode:emailCode.value});if(!current())return;update(value,token);binding.value=false;success.value='邮箱绑定成功';clearInterval(timer)}catch(e){if(current())error.value=e.message}finally{if(!disposed&&request===emailRequest)saving.value=false}
}
function accountChanged(event){if(event.key==='navy_token')load()}
onMounted(()=>{load();window.addEventListener('storage',accountChanged)})
onUnmounted(()=>{disposed=true;selectionVersion++;profileRequest++;settingsRequest++;uploadRequest++;emailRequest++;window.removeEventListener('storage',accountChanged);clearInterval(timer);clearPreview()})
</script>

<style scoped>
.naval-profile{color:#456a77;padding:0 3px 12px}
.profile-info{padding:12px 0 17px}
.profile-heading{display:flex;align-items:center;gap:14px;min-width:0}
.profile-avatar{position:relative;display:grid;place-items:center;width:64px;height:64px;flex-shrink:0;border:1px solid #cadce2;border-radius:50%;background:#e6eff2;color:#7897a4;transition:border-color .2s,box-shadow .2s}
.profile-avatar:hover{border-color:#d69c5d;box-shadow:0 0 0 3px #e9b77922}
.profile-avatar>img{width:100%;height:100%;object-fit:cover;border-radius:50%}
.profile-avatar>span{position:absolute;right:-2px;bottom:-2px;display:grid;place-items:center;width:23px;height:23px;border:2px solid #f9fcfb;border-radius:50%;background:#dc9751;color:white}
.profile-name{flex:1;min-width:0}
.profile-spectator-preference{margin-top:13px}.profile-spectator-preference>label{display:flex;align-items:center;gap:7px;min-height:30px;font-size:11px;color:#718b95}.profile-spectator-preference>label>span{flex:1;min-width:0}.profile-spectator-preference small{font-size:9px;color:#94a3a8;white-space:nowrap}.profile-spectator-preference input{flex-shrink:0}.profile-spectator-preference>p{font-size:10px;color:#b45f50;line-height:1.6;margin-top:4px;overflow-wrap:anywhere}
.profile-heading h3{font-size:18px;line-height:1.4;margin:0;overflow-wrap:anywhere;color:#345e6a}
.profile-settings-toggle{display:grid;place-items:center;flex-shrink:0;width:34px;height:34px;border:1px solid transparent;border-radius:4px;background:transparent;color:#829ca5}
.profile-settings-toggle:hover,.profile-settings-toggle[aria-expanded=true]{border-color:#efd8c0;background:var(--color-primary-bg,#fff2e6);color:var(--color-primary,#ee8d32)}
.profile-settings-toggle:focus-visible{outline:2px solid var(--color-primary,#ee8d32);outline-offset:2px}
.profile-account-settings{padding:17px 0 0;margin-top:16px;border-top:1px solid #dce6e9}
.profile-details{display:grid;grid-template-columns:60px minmax(0,1fr);gap:10px;margin:0 0 14px;font-size:11px;line-height:1.6}
.profile-details dt{display:flex;align-items:center;gap:7px;color:#849da7}
.profile-details dd{display:flex;align-items:center;gap:8px;min-width:0;margin:0;overflow-wrap:anywhere}.profile-details dd>span{flex:0 1 auto;min-width:0}.email-bind{display:inline-flex;align-items:center;gap:4px;flex-shrink:0;min-height:26px;padding:3px 0;background:transparent;color:var(--color-primary-strong);font-size:11px}.email-bind:hover{text-decoration:underline;text-underline-offset:3px}
.profile-button{display:flex;align-items:center;justify-content:center;gap:6px;min-height:36px;padding:7px 12px;background:#eaf1f3;border:1px solid #d4e1e5;border-radius:4px;color:#567d8b;font-size:12px;transition:background .2s,border-color .2s}
.profile-button:hover:not(:disabled){background:var(--color-primary-bg,#fff2e6);border-color:#e7bc92;color:#b76520}
.profile-button.primary{background:var(--color-primary,#ee8d32);border-color:var(--color-primary,#ee8d32);color:white}
.profile-button.primary:hover:not(:disabled){background:#d97b25;border-color:#d97b25;color:white}
.binding-form{display:grid;gap:12px;padding-top:18px;border-top:1px solid #dce6e9}
.binding-form h4{font-size:13px;font-weight:600}
.binding-form label{display:flex;flex-direction:column;gap:7px;font-size:12px;min-width:0}
.binding-form small{font-size:10px;color:#93a7af}
.verification-row{display:grid;grid-template-columns:minmax(0,1fr) 120px;gap:9px}
.verification-row>.profile-button{font-size:11px;padding:6px}
.captcha-image{height:44px;display:grid;place-items:center;overflow:hidden;background:#eaf3f5;border:1px solid #d0dfe4;border-radius:5px;color:#7897a4}
.captcha-image img{width:100%;height:100%;object-fit:fill}
.binding-actions{display:flex;gap:8px}
.profile-tabs{display:flex;align-items:center;gap:22px;min-height:42px;border-bottom:1px solid #dce6e9}.profile-tabs>button{position:relative;align-self:stretch;min-height:42px;padding:8px 0;background:transparent;border-bottom:2px solid transparent;font-size:12px;color:#879aa3}.profile-tabs>button[aria-selected=true]{color:var(--color-primary-strong);border-bottom-color:var(--color-primary);font-weight:600}.profile-inventory{padding:16px 0 0;min-width:0}
.profile-medals{padding:18px 0;border-bottom:1px solid #dce6e9}.profile-section-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;min-height:24px;margin:0}.profile-section-heading h3{display:flex;align-items:center;gap:7px;margin:0;color:#456a77;font-size:13px;font-weight:600;line-height:1.6}.profile-section-heading h3>svg{color:var(--color-primary);flex-shrink:0}
.profile-status{font-size:12px;padding:10px 0;line-height:1.7;overflow-wrap:anywhere}
.profile-status button{margin-left:8px;background:transparent;color:inherit;text-decoration:underline}
.error{color:#b45f50}.success{color:#b96e2e}
.profile-session{padding-top:16px;margin-top:18px;border-top:1px solid #e3ebee}.profile-logout{display:block;width:100%;font-size:12px;color:#8a9399;background:#f0f3f4;border:1px solid #e0e7ea;border-radius:4px;min-height:38px;padding:8px 12px;text-align:center}.profile-logout:hover{color:#b7654e;border-color:#e9cbbf;background:#fff4ef}
.avatar-inline-preview{padding-top:16px;margin-top:16px;border-top:1px solid #dce6e9}.inline-preview-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:14px}.inline-preview-heading h4{display:flex;align-items:center;gap:7px;font-size:12px;font-weight:600;color:#456a77}.inline-preview-heading h4>svg{color:var(--color-primary)}.preview-close{display:grid;place-items:center;width:28px;height:28px;border-radius:4px;background:transparent;color:#8b9da6}.preview-close:hover{background:var(--color-primary-bg);color:var(--color-primary-strong)}
.preview-heading{font-size:18px;line-height:1.5;color:#405c66;margin:0 25px 20px 0}.avatar-preview-stage{display:flex;align-items:center;justify-content:flex-start;gap:22px}.avatar-preview-square{width:136px;height:136px;max-width:47%;aspect-ratio:1;object-fit:cover;border:1px solid #e6dcd2;border-radius:6px;background:#edf1f2;flex-shrink:0}.avatar-preview-identity{display:flex;flex:1;flex-direction:column;align-items:center;justify-content:center;gap:9px;min-width:0}.avatar-preview-identity>img{flex:0 0 auto;width:48px;height:48px;border-radius:50%;object-fit:cover;border:1px solid #e4d5c8}.avatar-preview-identity>strong{font-size:12px;font-weight:500;overflow-wrap:anywhere;min-width:0;max-width:100%;text-align:center}.preview-actions,.logout-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px;flex-wrap:wrap;margin-top:16px}.preview-actions .reselect-avatar{margin-right:auto}.logout-question{font-size:13px;color:#7b8e95;line-height:1.7}
.profile-feedback-enter-active,.profile-feedback-leave-active{transition:opacity .2s,transform .2s}
.profile-feedback-enter-from,.profile-feedback-leave-to{opacity:0;transform:translateY(5px)}
.spinning{animation:profile-spin 1s linear infinite}
@keyframes profile-spin{to{transform:rotate(360deg)}}
@media(max-height:500px){.profile-info{padding-top:5px;padding-bottom:12px}.profile-avatar{width:52px;height:52px}.profile-heading h3{font-size:16px}}
@media(max-height:500px){.preview-heading{margin-bottom:12px}.avatar-preview-square{width:116px;height:116px}.avatar-inline-preview{padding-top:10px;margin-top:10px}.inline-preview-heading{margin-bottom:9px}}
@media(prefers-reduced-motion:reduce){*{transition:none!important;animation:none!important}}
</style>
