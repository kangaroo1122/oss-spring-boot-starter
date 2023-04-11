package com.kangaroohy.oss.configuration;

import com.kangaroohy.oss.constant.OssConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 类 OssProperties 功能描述：<br/>
 *	oss 配置信息 <br/>
 * @author kangaroo hy
 * @version 0.0.1
 * @date 2023/4/7 22:50
 */
@Data
@ConfigurationProperties(prefix = OssConstant.PREFIX)
public class OssProperties {

	/**
	 * 是否启用 oss，默认为：true
	 */
	private boolean enabled = true;

	/**
	 * 对象存储服务的URL
	 */
	private String endpoint;

	/**
	 * 自定义域名，配置此参数时，返回url优先使用
	 */
	private String customDomain;

	/**
	 * true path-style nginx 反向代理和S3默认支持<br/>
	 * 返回url如：https://{endpoint}/{bucketName} <br/>
	 * <br/>
	 * false virtual-host-style 阿里云、七牛云等云厂商OSS需要配置<br/>
	 * 返回url如：https://{bucketName}.{endpoint}
	 */
	private Boolean pathStyleAccess = true;

	/**
	 * 区域
	 */
	private String region = "cn-northwest-1";

	/**
	 * Access key就像用户ID，可以唯一标识你的账户
	 */
	private String accessKey;

	/**
	 * Secret key是你账户的密码
	 */
	private String secretKey;

	/**
	 * 默认的存储桶名称
	 */
	private String bucketName;

}
