'use strict';
(() => {
 const ns='http://www.w3.org/2000/svg',cache=new WeakMap();
 const observer=new ResizeObserver(entries=>entries.forEach(entry=>entry.target.querySelectorAll('.phrase').forEach(el=>cache.delete(el))));
 function geometry(el){
  let info=cache.get(el);if(info)return info;
  const button=el.closest('.sentence-button'),origin=button.getBoundingClientRect(),node=el.firstChild;
  if(!node||node.nodeType!==Node.TEXT_NODE)return null;
  let svg=button.querySelector('.reading-trace');
  if(!svg){svg=document.createElementNS(ns,'svg');svg.classList.add('reading-trace');svg.setAttribute('aria-hidden','true');button.append(svg);observer.observe(button);}
  svg.setAttribute('width',origin.width);svg.setAttribute('height',origin.height);
  let path=svg.querySelector(`[data-trace="${el.dataset.phrase}"]`);
  if(!path){path=document.createElementNS(ns,'path');path.dataset.trace=el.dataset.phrase;svg.append(path);}
  const range=document.createRange(),chars=[];
  for(let i=0;i<node.length;i++){range.setStart(node,i);range.setEnd(node,i+1);const r=range.getBoundingClientRect();chars.push({left:r.left-origin.left,right:r.right-origin.left,y:r.bottom-origin.top+1});}
  info={path,chars};cache.set(el,info);return info;
 }
 window.ReadingTrace={
  draw(el,progress,speaking){
   const info=geometry(el);if(!info)return;
   if(!speaking){info.path.style.opacity='0';return;}
   const read=info.chars.length*progress,segments=[];
   for(let i=0;i<Math.ceil(read)&&i<info.chars.length;i++){
    const char=info.chars[i],right=char.left+(char.right-char.left)*Math.min(1,read-i);
    const last=segments[segments.length-1];
    if(last&&Math.abs(last.y-char.y)<2)last.right=right;
    else segments.push({left:char.left,right,y:char.y});
   }
   info.path.setAttribute('d',segments.filter(s=>s.right>s.left).map(s=>`M${s.left.toFixed(2)},${s.y.toFixed(2)}H${s.right.toFixed(2)}`).join(' '));
   info.path.style.opacity='1';
  },
  clear(){document.querySelectorAll('.reading-trace path').forEach(path=>path.style.opacity='0');}
 };
})();
