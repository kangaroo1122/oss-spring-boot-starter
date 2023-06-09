## 3.0.1

- aws-java-sdk-s3 升级最新版 1.12.485
- 新增 `getPresignedMultipartUploadUrlsByPartNumbers` 方法，支持根据分片 partNumber 获取对应的文件上传预签名地址
- 其他细节优化

> `getPresignedMultipartUploadUrls` 一次返回所有分片预签名地址
> `getPresignedMultipartUploadUrlsByPartNumbers` 返回指定分片预签名地址

## 3.0.0

首次封装，提供以下方法

- aws-java-sdk-s3 集成最新版 1.12.444
- 默认内置三种bucket访问策略（只读，可读写，只写）
- 支持分片上传（Java代码上传）
- 支持后端预签名，前端 PUT 方式直传
- 支持后端预签名，前端 PUT 方式分片直传
- 适配spring boot 3.x，也支持 spring boot 2.x
