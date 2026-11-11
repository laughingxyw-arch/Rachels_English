package com.rachelsenglish.practice

import org.junit.Assert.*
import org.junit.Test

class PasskeyErrorTest {
 @Test fun unauthorizedDoesNotImplyAnExpiredLink(){
  assertEquals("身份验证未完成，请重试",passkeyHttpMessage(401,null))
  assertEquals("尚未关联学习记录，请从首次绑定链接进入",passkeyHttpMessage(401,"Setup required"))
  assertEquals("绑定链接已使用或过期，请重新获取",passkeyHttpMessage(401,"Setup link unavailable"))
 }
 @Test fun timeoutSignatureAndProfileErrorsRemainDistinct(){
  assertEquals("验证等待超时，请重新打开绑定链接",passkeyHttpMessage(401,"Challenge expired"))
  assertEquals("系统通行密钥校验未通过，请重试",passkeyHttpMessage(401,"Passkey verification failed"))
  assertEquals("请使用正式 App 对应的绑定链接",passkeyHttpMessage(401,"Wrong profile"))
  assertEquals("暂时无法连接，请稍后重试",passkeyHttpMessage(503,"Unexpected private details"))
 }
}
