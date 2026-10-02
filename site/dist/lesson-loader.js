'use strict';
if(window.PracticeApp){try{window.COURSES=JSON.parse(PracticeApp.getCourses());}catch{}}
const lessonId=new URLSearchParams(location.search).get('id')||'4dXbgvm4_7g';
const course=window.COURSES.find(item=>item.id===lessonId);
if(!course){location.replace('index.html');}else{
 const cover=document.createElement('img');cover.className='lesson-cover';cover.src=course.cover;cover.alt='';cover.width=96;cover.height=54;cover.style.viewTransitionName='course-cover';document.querySelector('.heading').prepend(cover);
 document.querySelector('h1').style.viewTransitionName='course-title';
 document.title=course.title+' · 原声跟读';
 document.querySelector('h1').innerHTML='';
 document.querySelector('h1').append(document.createTextNode(course.title));
 const label=document.createElement('span');label.textContent=course.label;document.querySelector('h1').append(label);
 document.querySelector('.source').href='https://www.youtube.com/watch?v='+course.id;
 document.querySelector('#dialogue').setAttribute('aria-busy','true');
 const data=document.createElement('script');data.src='lessons/'+course.id+'.js';
 data.onload=()=>{const app=document.createElement('script');app.src='app.js';app.onload=()=>document.querySelector('#dialogue').removeAttribute('aria-busy');document.body.append(app);};
 data.onerror=()=>{document.querySelector('#dialogue').textContent='课程文件未加载，请确认完整解压页面包。';};
 document.body.append(data);
}
