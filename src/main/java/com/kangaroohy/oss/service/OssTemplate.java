package com.kangaroohy.oss.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.kangaroohy.oss.configuration.OssProperties;
import com.kangaroohy.oss.entity.MultiPartUploadInfo;
import com.kangaroohy.oss.enums.PolicyType;
import com.kangaroohy.oss.utils.CustomUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 类 OssTemplate 功能描述：<br/>
 * aws-s3 通用存储操作 支持所有兼容s3协议的云存储: {阿里云OSS，腾讯云COS，七牛云，京东云，minio 等}
 *
 * @author kangaroo hy
 * @version 0.0.1
 * @date 2023/4/7 22:50
 */
@Service
public class OssTemplate {

    private final OssProperties ossProperties;

    private final AmazonS3 amazonS3;

    public OssTemplate(final OssProperties ossProperties, final AmazonS3 amazonS3) {
        this.ossProperties = ossProperties;
        this.amazonS3 = amazonS3;
    }

    /**
     * bucket是否存在
     *
     * @param bucketName bucket名称
     */
    public boolean existBucket(String bucketName) {
        return amazonS3.doesBucketExistV2(bucketName);
    }

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     */
    public boolean createBucket(String bucketName) {
        if (!amazonS3.doesBucketExistV2(bucketName)) {
            Bucket bucket = amazonS3.createBucket(bucketName);
            return bucket.getName() != null;
        }
        return true;
    }

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     * @param policyType policy
     */
    public boolean createBucket(String bucketName, PolicyType policyType) {
        boolean created = createBucket(bucketName);
        if (created) {
            amazonS3.setBucketPolicy(bucketName, PolicyType.getPolicy(policyType, bucketName));
        }
        return created;
    }

    /**
     * 获取全部bucket
     * <p>
     *
     * @see <a href=
     * "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/ListBuckets">AWS API
     * Documentation</a>
     */
    public List<Bucket> getAllBuckets() {
        return amazonS3.listBuckets();
    }

    /**
     * 获取指定bucket
     *
     * @param bucketName bucket名称
     * @see <a href=
     * "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/ListBuckets">AWS API
     * Documentation</a>
     */
    public Optional<Bucket> getBucket(String bucketName) {
        return amazonS3.listBuckets().stream().filter(b -> b.getName().equals(bucketName)).findFirst();
    }

    /**
     * 删除bucket
     *
     * @param bucketName bucket名称
     * @see <a href=
     * "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/DeleteBucket">AWS API
     * Documentation</a>
     */
    public void removeBucket(String bucketName) {
        amazonS3.deleteBucket(bucketName);
    }

    /**
     * 根据文件前置查询文件
     *
     * @param prefix     前缀
     * @see <a href=
     * "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/ListObjects">AWS API
     * Documentation</a>
     */
    public List<S3ObjectSummary> getAllObjectsByPrefix(String prefix) {
        return getAllObjectsByPrefix(getBucketName(), prefix);
    }

    /**
     * 根据文件前置查询文件
     *
     * @param bucketName bucket名称
     * @param prefix     前缀
     * @see <a href=
     * "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/ListObjects">AWS API
     * Documentation</a>
     */
    public List<S3ObjectSummary> getAllObjectsByPrefix(String bucketName, String prefix) {
        ObjectListing objectListing = amazonS3.listObjects(bucketName, prefix);
        return new ArrayList<>(objectListing.getObjectSummaries());
    }

    /**
     * 获取文件上传外链，只用于上传，有效期默认 10分钟
     *
     * @param objectName 文件名称
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration)
     */
    public String getPresignedObjectPutUrl(String objectName) {
        return getPresignedObjectPutUrl(getBucketName(), objectName);
    }

    /**
     * 获取文件上传外链，只用于上传，有效期默认 10分钟
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration)
     */
    public String getPresignedObjectPutUrl(String bucketName, String objectName) {
        return getPresignedObjectPutUrl(bucketName, objectName, 10);
    }

    /**
     * 获取文件上传外链，只用于上传
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @param time    过期时间，默认单位分钟,请注意该值必须小于7天
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration)
     */
    public String getPresignedObjectPutUrl(String bucketName, String objectName, Integer time) {
        return getPresignedObjectPutUrl(bucketName, objectName, time, TimeUnit.MINUTES);
    }

    /**
     * 获取文件上传外链，只用于上传
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @param time    过期时间,请注意该值必须小于7天
     * @param timeUnit    过期时间单位
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration)
     */
    public String getPresignedObjectPutUrl(String bucketName, String objectName, Integer time, TimeUnit timeUnit) {
        return getObjectUrl(bucketName, objectName, time, timeUnit, HttpMethod.PUT);
    }

    /**
     * 获取文件URL
     * <p>
     * If the object identified by the given bucket and key has public read permissions
     * (ex: {@link CannedAccessControlList#PublicRead}), then this URL can be directly
     * accessed to retrieve the object's data.
     *
     * @param objectName 文件名称
     * @return url
     */
    public String getObjectUrl(String objectName) {
        return getObjectUrl(getBucketName(), objectName);
    }

    /**
     * 获取文件URL
     * <p>
     * If the object identified by the given bucket and key has public read permissions
     * (ex: {@link CannedAccessControlList#PublicRead}), then this URL can be directly
     * accessed to retrieve the object's data.
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @return url
     */
    public String getObjectUrl(String bucketName, String objectName) {
        URL url = amazonS3.getUrl(bucketName, objectName);
        return url.toString();
    }

    /**
     * 获取文件外链，只用于下载
     *
     * @param objectName 文件名称
     * @param time    过期时间,请注意该值必须小于7天
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration)
     */
    public String getObjectUrl(String objectName, Integer time) {
        return getObjectUrl(getBucketName(), objectName, time);
    }

    /**
     * 获取文件外链，只用于下载
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @param time    过期时间,请注意该值必须小于7天
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration)
     */
    public String getObjectUrl(String bucketName, String objectName, Integer time) {
        return getObjectUrl(bucketName, objectName, time, TimeUnit.MINUTES);
    }

    /**
     * 获取文件外链，只用于下载
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @param time    过期时间,请注意该值必须小于7天
     * @param timeUnit    过期时间单位
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration)
     */
    public String getObjectUrl(String bucketName, String objectName, Integer time, TimeUnit timeUnit) {
        return getObjectUrl(bucketName, objectName, time, timeUnit, HttpMethod.GET);
    }

    /**
     * 获取文件外链
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @param time    过期时间,请注意该值必须小于7天
     * @param timeUnit    过期时间单位
     * @param method     文件操作方法：GET（下载）、PUT（上传）
     * @return url
     * @see AmazonS3#generatePresignedUrl(String bucketName, String key, Date expiration,
     * HttpMethod method)
     */
    public String getObjectUrl(String bucketName, String objectName, Integer time, TimeUnit timeUnit, HttpMethod method) {
        // Set the pre-signed URL to expire after `expires`.
        GeneratePresignedUrlRequest presignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectName).withMethod(method).withExpiration(CustomUtil.formDuration(time, timeUnit));
        // Generate the pre-signed URL.
        URL url = amazonS3.generatePresignedUrl(presignedUrlRequest);
        return url.toString();
    }

    /**
     * 上传文件
     *
     * @param objectName 文件名称
     * @param stream     文件流
     * @throws IOException IOException
     */
    public PutObjectResult putObject(String objectName, InputStream stream) throws IOException {
        return putObject(getBucketName(), objectName, stream);
    }

    /**
     * 上传文件
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @param stream     文件流
     * @throws IOException IOException
     */
    public PutObjectResult putObject(String bucketName, String objectName, InputStream stream) throws IOException {
        return putObject(bucketName, objectName, stream, stream.available(), "application/octet-stream");
    }

    /**
     * 上传文件 指定 contextType
     *
     * @param bucketName  bucket名称
     * @param objectName  文件名称
     * @param stream      文件流
     * @param contextType 文件类型
     * @throws IOException IOException
     */
    public PutObjectResult putObject(String bucketName, String objectName, String contextType, InputStream stream)
            throws IOException {
        return putObject(bucketName, objectName, stream, stream.available(), contextType);
    }

    /**
     * 上传文件
     *
     * @param bucketName  bucket名称
     * @param objectName  文件名称
     * @param stream      文件流
     * @param size        大小
     * @param contextType 类型
     * @see <a href= "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/PutObject">AWS
     * API Documentation</a>
     */
    public PutObjectResult putObject(String bucketName, String objectName, InputStream stream, int size, String contextType) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(size);
        objectMetadata.setContentType(contextType);
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, stream, objectMetadata);
        // Setting the read limit value to one byte greater than the size of stream will
        // reliably avoid a ResetException
        putObjectRequest.getRequestClientOptions().setReadLimit(size + 1);
        return amazonS3.putObject(putObjectRequest);
    }

    /**
     * 获取文件信息
     *
     * @param objectName 文件名称
     * @see <a href= "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/GetObject">AWS
     * API Documentation</a>
     */
    public S3Object getObjectInfo(String objectName) {
        return getObjectInfo(getBucketName(), objectName);
    }

    /**
     * 获取文件信息
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @see <a href= "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/GetObject">AWS
     * API Documentation</a>
     */
    public S3Object getObjectInfo(String bucketName, String objectName) {
        return amazonS3.getObject(bucketName, objectName);
    }

    /**
     * 获取文件
     *
     * @param objectName 文件名称
     * @return 二进制流
     * @see <a href= "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/GetObject">AWS
     * API Documentation</a>
     */
    public S3Object getObject(String objectName) {
        return getObject(getBucketName(), objectName);
    }

    /**
     * 获取文件
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @return 二进制流
     * @see <a href= "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/GetObject">AWS
     * API Documentation</a>
     */
    public S3Object getObject(String bucketName, String objectName) {
        return amazonS3.getObject(bucketName, objectName);
    }

    /**
     * 删除文件
     *
     * @param objectName 文件名称
     * @see <a href=
     * "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/DeleteObject">AWS API
     * Documentation</a>
     */
    public void removeObject(String objectName) {
        removeObject(getBucketName(), objectName);
    }

    /**
     * 删除文件
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @see <a href=
     * "http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/DeleteObject">AWS API
     * Documentation</a>
     */
    public void removeObject(String bucketName, String objectName) {
        amazonS3.deleteObject(bucketName, objectName);
    }

    /**
     * 获取前端分片上传预签名urls
     *
     * @param objectName 文件名称
     * @param partSize 分片大小
     * @return
     */
    public MultiPartUploadInfo getPresignedMultipartUploadUrls(String objectName, Integer partSize) {
        return getPresignedMultipartUploadUrls(objectName, partSize, "application/octet-stream");
    }

    /**
     * 获取前端分片上传预签名urls
     *
     * @param objectName 文件名称
     * @param partSize 分片大小
     * @param contentType Content-Type
     * @return
     */
    public MultiPartUploadInfo getPresignedMultipartUploadUrls(String objectName, Integer partSize, String contentType) {
        return getPresignedMultipartUploadUrls(getBucketName(), objectName, partSize, contentType);
    }

    /**
     * 获取前端分片上传预签名urls
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @param partSize 分片大小
     * @param contentType Content-Type
     * @return
     */
    public MultiPartUploadInfo getPresignedMultipartUploadUrls(String bucketName, String objectName, Integer partSize, String contentType) {
        return getPresignedMultipartUploadUrls(bucketName, objectName, partSize, contentType, 10);
    }

    /**
     * 获取前端分片上传预签名urls
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @param partSize 分片大小
     * @param contentType Content-Type
     * @param time 过期时间
     * @return
     */
    public MultiPartUploadInfo getPresignedMultipartUploadUrls(String bucketName, String objectName, Integer partSize, String contentType, Integer time) {
        return getPresignedMultipartUploadUrls(bucketName, objectName, partSize, contentType, time, TimeUnit.MINUTES);
    }

    /**
     * 获取前端分片上传预签名urls
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @param partSize 分片大小
     * @param contentType Content-Type
     * @param time 过期时间
     * @param timeUnit 过期时间单位，默认分钟
     * @return
     */
    public MultiPartUploadInfo getPresignedMultipartUploadUrls(String bucketName, String objectName, Integer partSize, String contentType, Integer time, TimeUnit timeUnit) {
        String uploadId;
        List<String> partUrlList = new ArrayList<>();
        try {
            InitiateMultipartUploadResult multipartUploadResult = this.initiateMultipartUpload(bucketName, objectName);
            uploadId = multipartUploadResult.getUploadId();
            GeneratePresignedUrlRequest presignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectName)
                    .withMethod(HttpMethod.PUT)
                    .withContentType(contentType)
                    .withExpiration(CustomUtil.formDuration(time, timeUnit));
            presignedUrlRequest.addRequestParameter("uploadId", uploadId);
            for (int i = 1; i <= partSize; i++) {
                presignedUrlRequest.addRequestParameter("partNumber", String.valueOf(i));
                URL url = amazonS3.generatePresignedUrl(presignedUrlRequest);
                partUrlList.add(url.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return MultiPartUploadInfo.builder()
                .uploadId(uploadId)
                .fileName(objectName)
                .expiryTime(CustomUtil.getLocalDateTime(time, timeUnit))
                .uploadUrls(partUrlList)
                .build();
    }

    /**
     * 初始化分片上传
     *
     * @param objectName 文件名称
     * @return
     */
    public InitiateMultipartUploadResult initiateMultipartUpload(String objectName) {
        return initiateMultipartUpload(getBucketName(), objectName);
    }

    /**
     * 初始化分片上传
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @return
     */
    public InitiateMultipartUploadResult initiateMultipartUpload(String bucketName, String objectName) {
        InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(bucketName, objectName);
        return amazonS3.initiateMultipartUpload(initiateMultipartUploadRequest);
    }

    /**
     * 初始化分片上传
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @param contentType contentType
     * @return
     */
    public InitiateMultipartUploadResult initiateMultipartUpload(String bucketName, String objectName, String contentType) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(contentType);
        InitiateMultipartUploadRequest initiateMultipartUploadRequest = new InitiateMultipartUploadRequest(bucketName, objectName, objectMetadata);
        return amazonS3.initiateMultipartUpload(initiateMultipartUploadRequest);
    }

    /**
     * 分片上传
     *
     * @param uploadId 分片唯一标识
     * @param objectName 文件名称
     * @param md5Digest md5 signature
     * @param partNumber 当前分片计数
     * @param partSize 当前分片文件大小
     * @param inputStream 文件流
     * @return
     */
    public UploadPartResult uploadPart(String uploadId, String objectName, String md5Digest, int partNumber, long partSize, InputStream inputStream) {
        return uploadPart(getBucketName(), uploadId, objectName, md5Digest, partNumber, partSize, inputStream);
    }

    /**
     * 分片上传
     *
     * @param bucketName bucketName
     * @param uploadId 分片唯一标识
     * @param objectName 文件名称
     * @param md5Digest md5 signature
     * @param partNumber 当前分片计数
     * @param partSize 当前分片文件大小
     * @param inputStream 文件流
     * @return
     */
    public UploadPartResult uploadPart(String bucketName, String uploadId, String objectName, String md5Digest, int partNumber, long partSize, InputStream inputStream) {
        UploadPartRequest uploadPartRequest = new UploadPartRequest()
                .withBucketName(bucketName)
                .withUploadId(uploadId)
                .withKey(objectName)
                .withMD5Digest(md5Digest)
                .withPartNumber(partNumber)
                .withPartSize(partSize)
                .withInputStream(inputStream);
        return amazonS3.uploadPart(uploadPartRequest);
    }

    /**
     * 列出已经上传完成的分段
     *
     * @param objectName 文件名称
     * @param uploadId 分片唯一标识
     * @return
     */
    public PartListing listParts(String objectName, String uploadId) {
        return listParts(getBucketName(), objectName, uploadId);
    }

    /**
     * 列出已经上传完成的分段
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @param uploadId 分片唯一标识
     * @return
     */
    public PartListing listParts(String bucketName, String objectName, String uploadId) {
        ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
        return amazonS3.listParts(listPartsRequest);
    }

    /**
     * 合并分片
     *
     * @param objectName 文件名称
     * @param uploadId 分片唯一标识
     * @return
     */
    public CompleteMultipartUploadResult completeMultipartUpload(String objectName, String uploadId) {
        return completeMultipartUpload(getBucketName(), objectName, uploadId);
    }

    /**
     * 合并分片
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @param uploadId 分片唯一标识
     * @return
     */
    public CompleteMultipartUploadResult completeMultipartUpload(String bucketName, String objectName, String uploadId) {
        List<PartETag> partETags = new ArrayList<>();
        PartListing partListing = listParts(bucketName, objectName, uploadId);
        List<PartSummary> parts = partListing.getParts();
        if (!parts.isEmpty()) {
            for (PartSummary summary : parts) {
                partETags.add(new PartETag(summary.getPartNumber(), summary.getETag()));
            }
        }
        return completeMultipartUpload(bucketName, objectName, uploadId, partETags);
    }

    /**
     * 合并分片
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @param uploadId 分片唯一标识
     * @param partETags 分片文件etags集合
     * @return
     */
    public CompleteMultipartUploadResult completeMultipartUpload(String bucketName, String objectName, String uploadId, List<PartETag> partETags) {
        CompleteMultipartUploadRequest completeMultipartUploadRequest = new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, partETags);
        return amazonS3.completeMultipartUpload(completeMultipartUploadRequest);
    }

    /**
     * 终止分片上传
     *
     * @param objectName 文件名称
     * @param uploadId 分片唯一标识
     */
    public void abortMultipartUpload(String objectName, String uploadId) {
        abortMultipartUpload(getBucketName(), objectName, uploadId);
    }

    /**
     * 终止分片上传
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @param uploadId 分片唯一标识
     */
    public void abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        AbortMultipartUploadRequest abortMultipartUploadRequest = new AbortMultipartUploadRequest(bucketName, objectName, uploadId);
        amazonS3.abortMultipartUpload(abortMultipartUploadRequest);
    }

    /**
     * 列出正在进行的分段上传
     *
     * @return
     */
    public MultipartUploadListing listMultipartUploads() {
        return listMultipartUploads(getBucketName());
    }

    /**
     * 列出正在进行的分段上传
     *
     * @param bucketName bucketName
     * @return
     */
    public MultipartUploadListing listMultipartUploads(String bucketName) {
        ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(bucketName);
        return amazonS3.listMultipartUploads(listMultipartUploadsRequest);
    }

    /**
     * 默认BucketName
     *
     * @return
     */
    public String getBucketName() {
        if (ossProperties.getBucketName() == null) {
            throw new RuntimeException("未配置默认 BucketName");
        }
        return ossProperties.getBucketName();
    }

    /**
     * 获得外网访问地址，需要配置bucket访问权限
     *
     * @param objectName 文件名称
     * @return
     */
    public String getGatewayUrl(String objectName) {
        return getGatewayUrl(getBucketName(), objectName);
    }

    /**
     * 获得外网访问地址，需要配置bucket访问权限
     *
     * @param bucketName bucketName
     * @param objectName 文件名称
     * @return
     */
    public String getGatewayUrl(String bucketName, String objectName) {
        if (StringUtils.hasText(ossProperties.getCustomDomain())) {
            return ossProperties.getCustomDomain() + "/" + objectName;
        }
        String url = ossProperties.getEndpoint() + "/" + bucketName;
        if (ossProperties.getPathStyleAccess().equals(Boolean.FALSE)) {
            url = CustomUtil.convertToVirtualHostEndpoint(URI.create(ossProperties.getEndpoint()), bucketName).toString();
        }
        return url + "/" + objectName;
    }

}
