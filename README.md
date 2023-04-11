## oss-spring-boot-starter

兼容S3 协议的通用文件存储工具类 ，支持 兼容S3 协议的云存储 

- MINIO
- 阿里云
- 华为云
- 腾讯云
- 京东云
- 七牛云

...

## spring boot starter依赖

## maven

[![Maven Central](https://img.shields.io/maven-central/v/com.kangaroohy/oss-spring-boot-starter.svg)](https://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.kangaroohy%22%20AND%20a%3A%oss-spring-boot-starter%22)

适配 SpringBoot3.x，也支持 SpringBoot2.x

```xml
<dependency>
    <groupId>com.kangaroohy</groupId>
    <artifactId>oss-spring-boot-starter</artifactId>
    <version>${lastVersion}</version>
</dependency>
```

## 使用方法

### 配置文件

```yaml
kangaroohy:
    oss:
      endpoint: https://demo.kangaroohy.com
      access-key: admin
      secret-key: admin123
```
> path-style-access: false 时，也就是 virtual-host-style
> - https://{bucketName}.{endpoint}
> - 如：https://{bucketName}.demo.kangaroohy.com
> 
> path-style-access: true 时
> - https://{endpoint}/{bucketName}
> - 如：https://demo.kangaroohy.com/{bucketName}

### 代码使用

注入OssTemplate，调用相关方法即可

```java
    @Autowired
    private OssTemplate ossTemplate;
```

封装方法不满足时，可自行注入 AmazonS3 ，调用原始方法

~~~java
    @Autowired
    private AmazonS3 amazonS3;
~~~

## 前端PUT直传

后端，获取上传的url，前端 PUT 方式直接请求

> 注：七牛云、腾讯云等云服务器厂商可能需要配置跨域

~~~java
    @GetMapping("/upload-url")
    @Operation(summary = "获取Put直传地址")
    public RestResult<String> uploadUrl(@RequestParam String fileName) {
        String url = ossTemplate.getPresignedObjectPutUrl(fileName);
        return RestResult.ok(url);
    }
~~~

## 分片上传

*推荐方式二*

### 方式一 后端接收分片，再上传oss服务器

思路：请求后端获取分片标识，前端分片，请求后端接口上传分片，上传完成，再请求合并分片的接口

~~~java
    @GetMapping("/upload-id")
    @Operation(summary = "获取分片upload id")
    public RestResult<String> uploadId(@RequestParam String fileName, @RequestParam String contentType) {
        InitiateMultipartUploadResult uploadResult = ossTemplate.initiateMultipartUpload(ossTemplate.getBucketName(), fileName, contentType);    
        return RestResult.ok(uploadResult.getUploadId());
    }

    @PutMapping("/upload-part")
    @Operation(summary = "分片上传")
    public RestResult<String> uploadPart(@RequestPart MultipartFile file, @RequestPart @Validated UploadPartInfoBO partInfo) {
        byte[] md5s;
        long partSize;
        ByteArrayInputStream inputStream;
        try {
            md5s = MessageDigest.getInstance("MD5").digest(file.getBytes());
            partSize = file.getSize();
            inputStream = new ByteArrayInputStream(file.getBytes());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new FailException("分块上传MD5加密出错");
        }
        ossTemplate.uploadPart(partInfo.getUploadId(), partInfo.getFileName(), Base64.encodeAsString(md5s), partInfo.getPartNumber(), partSize, inputStream);
        return RestResult.ok();
    }

    @GetMapping("/merge-part")
    @Operation(summary = "分片直传完成合并")
    public RestResult<String> mergePart(@RequestParam String fileName, @RequestParam String uploadId) {
        ossTemplate.completeMultipartUpload(fileName, uploadId);
        return RestResult.ok(ossTemplate.getGatewayUrl(fileName));
    }
~~~

~~~java
@Data
public class UploadPartInfoBO {

    @NotNull(message = "uploadId is required")
    private String uploadId;

    @NotNull(message = "fileName is required")
    private String fileName;

    @NotNull(message = "partNumber is required")
    private Integer partNumber;
}
~~~

### 方式二 前端直传oss服务器

> 注：云服务商oss可能需要配置跨域

思路：前端请求后端，获取各个分片预签名 url，直接 PUT 方式请求上传 oss 服务器，上传完成，请求后端合并分片

~~~java
    @GetMapping("/part-url")
    @Operation(summary = "获取分片直传地址")
    public RestResult<MultiPartUploadInfo> partUrl(@RequestParam String fileName, @RequestParam int partSize, @RequestParam String contentType) {
        MultiPartUploadInfo uploadInfo = ossTemplate.getPresignedMultipartUploadUrls(fileName, partSize, contentType);
        return RestResult.ok(uploadInfo);
    }

    @GetMapping("/merge-part")
    @Operation(summary = "分片直传完成合并")
    public RestResult<String> mergePart(@RequestParam String fileName, @RequestParam String uploadId) {
        ossTemplate.completeMultipartUpload(fileName, uploadId);
        return RestResult.ok(ossTemplate.getGatewayUrl(fileName));
    }
~~~
