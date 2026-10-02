'use strict';
const htmlEscape=text=>String(text).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function renderCourses(query=''){
 if(window.PracticeApp){try{window.COURSES=JSON.parse(PracticeApp.getCourses());}catch{}}
 const courses=window.COURSES.filter(c=>[c.title,c.label,c.videoTitle,c.excerpt].join(' ').toLowerCase().includes(query.toLowerCase()));
 document.querySelector('.library-search').hidden=window.COURSES.length<6;
 document.querySelector('#course-count').textContent=`${window.COURSES.length} 门课程`;
 document.querySelector('#course-list').innerHTML=courses.map(c=>`<a class="course-link" data-course="${htmlEscape(c.id)}" href="lesson.html?id=${encodeURIComponent(c.id)}"><div class="course-cover"><img src="${htmlEscape(c.cover)}" alt="${htmlEscape(c.videoTitle)}" width="1280" height="720" decoding="async"><span class="course-length">${Math.round(c.sourceSeconds)} 秒原声</span><span class="cover-play" aria-hidden="true"><svg viewBox="0 0 24 24" width="24" height="24"><path d="m9 5 11 7-11 7Z" fill="currentColor"/></svg></span></div><div class="course-content"><h2>${htmlEscape(c.title)}</h2><div class="course-meta"><span>${htmlEscape(c.label)} · ${c.groupCount} 句</span><time datetime="${htmlEscape(c.added)}">${htmlEscape(c.added.replaceAll('-','.'))}</time></div></div></a>`).join('');
 document.querySelector('#no-results').hidden=courses.length!==0;
 markCourse(sessionStorage.getItem('selected-course'));
}
document.querySelector('.library-search').hidden=window.COURSES.length<6;
document.querySelector('#search').addEventListener('input',event=>renderCourses(event.target.value));renderCourses();

window.refreshCourses=()=>renderCourses(document.querySelector('#search').value);
if(window.PracticeApp){
 const sync=document.createElement('button');sync.className='sync-courses';sync.setAttribute('aria-label','检查新课程');sync.title='检查新课程';sync.innerHTML='<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M20 7v5h-5M4 17v-5h5"/><path d="M6.5 6a8 8 0 0 1 13 4M4.5 14a8 8 0 0 0 13 4"/></svg>';sync.onclick=()=>PracticeApp.syncCourses();document.querySelector('.library-title').append(sync);
 document.querySelector('#course-list').addEventListener('click',event=>{const link=event.target.closest('.course-link');if(link){event.preventDefault();PracticeApp.openCourse(new URL(link.href).searchParams.get('id'));}});
}

function markCourse(id){document.querySelectorAll('.course-cover,.course-content h2').forEach(el=>el.style.viewTransitionName='none');const link=[...document.querySelectorAll('.course-link')].find(el=>el.dataset.course===id);if(link){link.querySelector('.course-cover').style.viewTransitionName='course-cover';link.querySelector('h2').style.viewTransitionName='course-title';}}
markCourse(sessionStorage.getItem('selected-course'));
document.querySelector('#course-list').addEventListener('click',event=>{const link=event.target.closest('.course-link');if(link){sessionStorage.setItem('selected-course',link.dataset.course);markCourse(link.dataset.course);}},true);
addEventListener('pagereveal',()=>markCourse(sessionStorage.getItem('selected-course')));
