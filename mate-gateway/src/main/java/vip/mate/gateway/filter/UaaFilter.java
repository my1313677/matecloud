package vip.mate.gateway.filter;

import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vip.mate.core.cloud.props.MateUaaProperties;
import vip.mate.core.common.constant.MateConstant;
import vip.mate.core.common.constant.Oauth2Constant;
import vip.mate.core.common.util.ResponseUtil;
import vip.mate.core.common.util.SecurityUtil;
import vip.mate.core.common.util.StringPool;
import vip.mate.core.common.util.TokenUtil;

/**
 * 网关统一的token验证
 *
 * @author pangu
 */
@Slf4j
@Component
@AllArgsConstructor
public class UaaFilter implements GlobalFilter, Ordered {

	private final MateUaaProperties mateUaaProperties;

	/**
	 * 路径前缀以/mate开头，如mate-system
	 */
	public static final String PATH_PREFIX = "/mate";

	/**
	 * 索引自1开头检索，跳过第一个字符就是检索的字符的问题
	 */
	public static final int FROM_INDEX = 1;



	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		// 如果未启用网关验证，则跳过
		if (!mateUaaProperties.getEnable()) {
			return chain.filter(exchange);
		}
		log.error("getIgnoreUrl:{}", mateUaaProperties.getIgnoreUrl());

		//　如果在忽略的url里，则跳过
		String path = replacePrefix(exchange.getRequest().getURI().getPath());
		String requestUrl = exchange.getRequest().getURI().getRawPath();
		if (ignore(path) || ignore(requestUrl)) {
			return chain.filter(exchange);
		}

		// 验证token是否有效
		ServerHttpResponse resp = exchange.getResponse();
		String headerToken = exchange.getRequest().getHeaders().getFirst(Oauth2Constant.HEADER_TOKEN);
		if (headerToken == null) {
			return unauthorized(resp, "没有携带Token信息！");
		}
		Claims claims = SecurityUtil.getClaims(TokenUtil.getToken(headerToken));
		if (claims == null) {
			return unauthorized(resp, "token已过期或验证不正确！");
		}
		return chain.filter(exchange);
	}

	/**
	 * 检查是否忽略url
	 * @param path 路径
	 * @return boolean
	 */
	private boolean ignore(String path) {
		return mateUaaProperties.getIgnoreUrl().stream()
				.map(url -> url.replace("/**", ""))
				.anyMatch(path::startsWith);
	}

	/**
	 * 移除模块前缀
	 * @param path 路径
	 * @return String
	 */
	private String replacePrefix(String path) {
		if (path.startsWith(PATH_PREFIX)) {
			return path.substring(path.indexOf(StringPool.SLASH, FROM_INDEX));
		}
		return path;
	}

	private Mono<Void> unauthorized(ServerHttpResponse resp, String msg) {
		return ResponseUtil.webFluxResponseWriter(resp, MateConstant.JSON_UTF8, HttpStatus.UNAUTHORIZED, msg); }

	@Override
	public int getOrder() {
		return MateConstant.MATE_UAA_FILTER_ORDER;
	}

	public static void main(String[] args) {

	}
}
