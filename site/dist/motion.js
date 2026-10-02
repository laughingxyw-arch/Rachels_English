'use strict';
(() => {
 const preference=matchMedia('(prefers-reduced-motion: reduce)');
 const reduced=()=>preference.matches||Boolean(window.PracticeApp?.reducedMotion?.());
 const sync=()=>document.documentElement.toggleAttribute('data-reduced-motion',reduced());
 sync();preference.addEventListener('change',sync);addEventListener('pageshow',sync);
 // Each spring retains position and velocity when its target changes.
 function spring(draw,initial=0){let x=initial,v=0,target=initial,frame=0,last=0;const tick=time=>{const dt=Math.min((time-last)/1000||1/60,.032);last=time;v+=(340*(target-x)-32*v)*dt;x+=v*dt;draw(x);if(Math.abs(target-x)+Math.abs(v)>.02)frame=requestAnimationFrame(tick);else{x=target;v=0;frame=0;draw(x);}};return{to(value){target=value;if(reduced()){cancelAnimationFrame(frame);frame=0;x=target;v=0;draw(x);}else if(!frame){last=performance.now();frame=requestAnimationFrame(tick);}},set(value){cancelAnimationFrame(frame);frame=0;x=target=value;v=0;draw(x);}};}
 const presses=new WeakMap();
 document.addEventListener('pointerdown',event=>{const el=event.target.closest('button,summary,.course-link');if(!el||el.classList.contains('sentence-button'))return;let motion=presses.get(el);if(!motion){motion=spring(x=>el.style.setProperty('scale',String(x)),1);presses.set(el,motion);}motion.to(.965);const reset=()=>{motion.to(1);removeEventListener('pointerup',reset);removeEventListener('pointercancel',reset);};addEventListener('pointerup',reset,{once:true});addEventListener('pointercancel',reset,{once:true});});
 const modes=document.querySelector('.modes');
 if(modes){const plate=document.createElement('span');plate.className='mode-plate';plate.setAttribute('aria-hidden','true');modes.prepend(plate);const move=spring(x=>plate.style.transform=`translateX(${x}px)`);const position=()=>{const selected=modes.querySelector('.selected');if(selected){plate.style.width=selected.offsetWidth+'px';move.to(selected.offsetLeft-4);}};new MutationObserver(position).observe(modes,{subtree:true,attributes:true,attributeFilter:['class']});new ResizeObserver(position).observe(modes);position();}
 const settings=document.querySelector('.settings');
 if(settings){
  const panel=settings.querySelector('.settings-panel'),handle=panel.querySelector('.settings-heading'),trigger=settings.querySelector('summary');
  let start=0,drag=0,down=false,closing=null;
  const motion=spring(y=>{panel.style.transform=`translateY(${y}px) scale(${1-Math.min(Math.max(y,0)/2500,.05)})`;panel.style.opacity=String(1-Math.min(Math.max(y,0)/420,.5));});
  const finishClose=focus=>{settings.open=false;trigger.setAttribute('aria-expanded','false');motion.set(0);if(focus)trigger.focus();};
  window.closePracticeSettings=(focus=false)=>{
   if(!settings.open||closing)return;
   if(reduced()){finishClose(focus);return;}
   const style=getComputedStyle(panel);
   closing=panel.animate([{transform:style.transform,opacity:style.opacity},{transform:'translateY(18px) scale(.98)',opacity:0}],{duration:160,easing:'cubic-bezier(.3,0,.8,.4)'});
   closing.finished.then(()=>{finishClose(focus);closing=null;},()=>{closing=null;});
  };
  trigger.addEventListener('click',event=>{
   if(closing){event.preventDefault();closing.cancel();motion.to(0);return;}
   if(settings.open){event.preventDefault();window.closePracticeSettings(true);}
  });
  settings.addEventListener('toggle',()=>{if(settings.open){motion.set(reduced()?0:18);motion.to(0);}else motion.set(0);});
  handle.addEventListener('pointerdown',event=>{if(event.target.closest('button')||event.button!==0)return;if(closing)closing.cancel();start=event.clientY;drag=0;down=true;handle.setPointerCapture(event.pointerId);motion.set(0);});
  handle.addEventListener('pointermove',event=>{if(down){drag=Math.max(-12,event.clientY-start);motion.set(drag);}});
  const finish=event=>{if(!down)return;down=false;if(handle.hasPointerCapture(event.pointerId))handle.releasePointerCapture(event.pointerId);if(event.type!=='pointercancel'&&drag>72)window.closePracticeSettings(true);else motion.to(0);};
  handle.addEventListener('pointerup',finish);handle.addEventListener('pointercancel',finish);
 }
 addEventListener('pageswap',()=>{
  const cover=document.querySelector('.lesson-cover');
  if(cover){const r=cover.getBoundingClientRect();if(r.bottom<=0||r.top>=innerHeight){cover.style.viewTransitionName='none';const title=document.querySelector('.heading h1');if(title)title.style.viewTransitionName='none';}}
 });
 // Navigations can be interrupted instead of locking the interface behind snapshots.
 addEventListener('pagereveal',event=>{if(!event.viewTransition)return;if(reduced())event.viewTransition.skipTransition();else{const interrupt=()=>event.viewTransition.skipTransition();addEventListener('pointerdown',interrupt,{once:true});event.viewTransition.finished.finally(()=>removeEventListener('pointerdown',interrupt));}});
})();
