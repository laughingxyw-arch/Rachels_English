/* Same RP and verified API as the native app. No session token is stored in the browser. */
(()=>{
 const button=document.getElementById('save'),status=document.getElementById('status');
 const valid=s=>typeof s==='string'&&/^[a-f0-9]{64}$/.test(s);
 const storageKey='pending-'+location.pathname,mode=location.pathname==='/passkey-probe/'?'probe':'owner';
 let ticket=location.hash.slice(1),pending=null,busy=false;
 try{if(!valid(ticket)){const cached=JSON.parse(sessionStorage.getItem(storageKey)||'null');if(cached?.expires>Date.now())ticket=cached.ticket;}}catch{}
 if(!valid(ticket))return;
 if(!window.isSecureContext||!window.PublicKeyCredential||!navigator.credentials?.create){status.textContent='当前浏览器不支持系统通行密钥，请使用支持通行密钥的浏览器打开本页。';return;}
 button.disabled=false;
 const decode=s=>Uint8Array.from(atob(s.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));
 const encode=b=>{let s='';for(const c of new Uint8Array(b))s+=String.fromCharCode(c);return btoa(s).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');};
 const post=async(path,body)=>{
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),20000);
  try{
   const r=await fetch('/api/passkey/'+path,{method:'POST',headers:{'Content-Type':'application/json'},cache:'no-store',credentials:'omit',signal:controller.signal,body:JSON.stringify(body)});
   const data=await r.json();if(!r.ok){const e=new Error('Request failed');e.reason=data.error;e.httpStatus=r.status;throw e;}return data;
  }finally{clearTimeout(timer);}
 };
 button.addEventListener('click',async()=>{
  if(busy)return;busy=true;button.disabled=true;
  try{
   if(!pending){
    status.textContent='正在准备系统验证…';
    const challenge=await post('register/options',{ticket,mode}),o=challenge.options;
    o.challenge=decode(o.challenge);o.user.id=decode(o.user.id);
    o.excludeCredentials=(o.excludeCredentials||[]).map(k=>({...k,id:decode(k.id)}));
    status.textContent='请按系统提示保存通行密钥并验证身份。';
    const credential=await navigator.credentials.create({publicKey:o});
    if(!credential)throw new DOMException('Cancelled','NotAllowedError');
    const response=credential.response;
    // Manual serialization also supports providers predating PublicKeyCredential.toJSON().
    const json={id:credential.id,rawId:encode(credential.rawId),type:credential.type,authenticatorAttachment:credential.authenticatorAttachment,
     clientExtensionResults:credential.getClientExtensionResults(),response:{clientDataJSON:encode(response.clientDataJSON),attestationObject:encode(response.attestationObject),transports:response.getTransports?.()||[]}};
    pending={ticket,mode,challengeId:challenge.challengeId,credential:json};
   }
   status.textContent='正在确认绑定…';
   await post('register/finish',pending);
   pending=null;ticket=null;
   try{sessionStorage.removeItem(storageKey);}catch{}
   history.replaceState(null,'',location.pathname+location.search);
   button.textContent='已保存';
   const open=document.getElementById('open');
   if(open?.href)open.href=open.href.replace(/S\.enrollment=[a-f0-9]{64};/,'');
   status.textContent=mode==='probe'?'通行密钥已关联。请从桌面打开独立验证 App，使用恢复入口验证。':'通行密钥已关联。请从桌面打开蓝色「原声练习」App，在「学习记录 → … → 云端同步」点「验证并恢复」。';
  }catch(e){
   if(e.httpStatus&&e.httpStatus<500)pending=null;
   const messages={'Setup link unavailable':'绑定链接已使用或过期。如果刚才已经保存，请直接打开 App 验证并恢复。','Challenge expired':'验证已超时，请重新点击保存。','Passkey verification failed':'通行密钥校验未通过，请重新保存。','Wrong profile':'绑定入口与当前记录不匹配。'};
   status.textContent=messages[e.reason]||(e.name==='NotAllowedError'?'系统验证已取消或未完成，可重新点击保存。':e.name==='NotSupportedError'?'当前系统凭据服务不支持此通行密钥，请检查系统密码与通行密钥设置。':e.name==='SecurityError'?'浏览器未允许本页使用通行密钥，请在正式 HTTPS 页面重试。':'未能完成绑定，请检查网络后重试。');
   button.textContent=pending?'重试确认绑定':'保存通行密钥';
  }finally{busy=false;button.disabled=ticket===null;}
 });
})();
