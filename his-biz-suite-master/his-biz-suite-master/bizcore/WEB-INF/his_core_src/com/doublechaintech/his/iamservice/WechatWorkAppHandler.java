package com.doublechaintech.his.iamservice;

import java.util.Map;

import com.doublechaintech.his.HisUserContext;
import com.doublechaintech.his.MultipleAccessKey;
import com.doublechaintech.his.SmartList;
import com.doublechaintech.his.secuser.SecUser;
import com.doublechaintech.his.wechatworkappidentify.WechatWorkappIdentify;
import com.terapico.utils.*;

import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.bean.WxCpMaJsCode2SessionResult;

@SuppressWarnings("unchecked")
public class WechatWorkAppHandler extends BaseIdentificationHandler {

	protected WxCpService wxService;
	
	public WxCpService getWxService() {
		return wxService;
	}
	public void setWxService(WxCpService wxService) {
		this.wxService = wxService;
	}


	@Override
	public LoginResult authenticate(HisUserContext userContext, LoginContext loginContext) {
		LoginResult result = new LoginResult();
		result.setLoginContext(loginContext);
		// 校验输入
		String code = loginContext.getLoginData().getCode();
		if (TextUtil.isBlank(code)) {
			return result.withError("请先通过企业微信小程序获取用户code,再进行登录");
		}
		// 进行认证
		try {
			if(!authenticateWithCode(userContext, code, result)) {
				return result.withError(result.getMessage());
			}
			Map<String, String> additionalInfo = (Map<String, String>) result.getLoginContext().getLoginTarget().getAdditionalInfo();
			String userId = additionalInfo.get("userId");
			SmartList<WechatWorkappIdentify> rcdList = getIdentifyRecords(userContext, result.getLoginContext());
			if (rcdList == null) {
				result.setAuthenticated(true);
				result.setSuccess(false);
				result.setMessage("企业微信用户"+userId+"未注册");
				return result;
			}
			
			if (rcdList.size() > 1) {
				result.setAuthenticated(false);	// 虽然知道你是个微信用户,但是我还是不知道你是哪位
				result.setSuccess(false);
				result.setMessage("企业微信用户"+userId+"关联了多个用户,无法确定唯一身份,请用其他方式登录或联系管理员处理账号.");
				return result;
			}
			
			SecUser secUser = userContext.getDAOGroup().getSecUserDAO().load(rcdList.first().getSecUser().getId(), EO);
			result.getLoginContext().getLoginTarget().setSecUser(secUser);
			result.setAuthenticated(true);
			result.setSuccess(true);
			return result.authenticated("企业微信用户验证成功");
			
		} catch (Exception e) {
			e.printStackTrace();
			return result.withError("企业微信认证失败:"+e.getMessage());
		}
	}


	protected SmartList<WechatWorkappIdentify> getIdentifyRecords(HisUserContext userContext,
			LoginContext loginContext) {
		Map<String, String> additionalInfo = (Map<String, String>) loginContext.getLoginTarget().getAdditionalInfo();
		String userId = additionalInfo.get("userId");
		// String userSessionKey = additionalInfo.get("sessionKey");
		String corpId = additionalInfo.get("corpId");
		MultipleAccessKey key = new MultipleAccessKey();
		key.put(WechatWorkappIdentify.USER_ID_PROPERTY, userId);
		key.put(WechatWorkappIdentify.CORP_ID_PROPERTY, corpId);
		SmartList<WechatWorkappIdentify> rcdList = userContext.getDAOGroup().getWechatWorkappIdentifyDAO()
				.findWechatWorkappIdentifyWithKey(key, EO);
		return rcdList;
	}

	protected boolean authenticateWithCode(HisUserContext userContext, String code, LoginResult result) throws Exception {
		WxCpMaJsCode2SessionResult sessionInfo = wxService.jsCode2Session(code);
		String userId = sessionInfo.getUserId();
        String userSessionKey = sessionInfo.getSessionKey();
        String corpId = wxService.getWxCpConfigStorage().getCorpId();
        
        String cacheKey = this.getWehatSessionKeyCacheKey(userContext, corpId, userId);
		userContext.putToCache(cacheKey, userSessionKey, (int) (1*DateTimeUtil.HOUR_IN_MS/DateTimeUtil.SECOND_IN_MS));
		
		Map<String, String> additionalInfo = MapUtil.put("userId", userId).put("sessionKey", userSessionKey)
				.put("corpId", corpId).into_map(String.class);
		result.getLoginContext().getLoginTarget().setAdditionalInfo(additionalInfo);
		result.setAuthenticated(true);
		return true;
	}

	@Override
	public void bindWithSecUser(HisUserContext userContext, LoginContext loginContext) throws Exception {
		SecUser secUser = loginContext.getLoginTarget().getSecUser();
		if (secUser == null) {
			throw new Exception("please call this only after secUser was found");
		}
		Map<String, String> additionalInfo = (Map<String, String>) loginContext.getLoginTarget().getAdditionalInfo();
		String userId = additionalInfo.get("userId");
		// String userSessionKey = additionalInfo.get("sessionKey");
		String corpId = additionalInfo.get("corpId");
		SmartList<WechatWorkappIdentify> rcdList = getIdentifyRecords(userContext, loginContext);
		if (rcdList != null && rcdList.stream().anyMatch(it -> it.getSecUser().getId().equals(secUser.getId()))) {
			// 要提防有重复的
			return;
		}
		userContext.getManagerGroup().getWechatWorkappIdentifyManager().createWechatWorkappIdentify(userContext, corpId,
				userId, secUser.getId(), userContext.now());
	}

}

















