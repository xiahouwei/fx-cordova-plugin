/**
 * var weixin = navigator.weixin; weixin.generatePrepayId( {"body":"x5",
 * "feeType":"1", "notifyUrl":"http://www.justep.com", "totalFee":"1",
 * "traceId":'123456', "tradeNo":"123456789"},function(prepayId){
 * console.log('prepayId:' + prepayId); weixin.sendPayReq(prepayId,function(){
 * console.log('prepayId success'); alert("success"); },function(message){
 * alert("sendPayReq:"+ message); }); },function(message){ alert("getPrepayId:" +
 * message); });
 * 
 * weixin.share({ message: { title: "Message Title", description: "Message
 * Description(optional)", mediaTagName: "Media Tag Name(optional)", thumb:
 * "http://YOUR_THUMBNAIL_IMAGE", media: { type: weixin.Type.WEBPAGE, // webpage
 * webpageUrl: "https://www.justep.com" // webpage } }, scene:
 * weixin.Scene.TIMELINE // share to Timeline }, function () { alert("Success"); },
 * function (reason) { alert("Failed: " + reason); });
 * 
 */
var exec = require('cordova/exec');

var weixin_secret = "";

module.exports = {
	Scene : {
		SESSION : 0, // 聊天界面
		TIMELINE : 1, // 朋友圈
		FAVORITE : 2
	// 收藏
	},
	Type : {
		APP : 1,
		EMOTION : 2,
		FILE : 3,
		IMAGE : 4,
		MUSIC : 5,
		VIDEO : 6,
		WEBPAGE : 7
	},

	error : "",
	code : "",
	token : {
		"access_token" : "",
		"openid" : ""
	},
	share : function(message, onSuccess, onError) {
		exec(onSuccess, onError, "Weixin", "share", [ message ]);
	},
	LaunchMini : function(minidata, onSuccess, onError) {
		exec(onSuccess, onError, "Weixin", "LaunchMini", [ minidata ]);
	},
	getParam : function(onSuccess, onError) {
		var self = this;
		function successCallback(params) {
			weixin_secret = params.weixin_secret
			delete params.app;
			onSuccess(params);
		}
		exec(successCallback, onError, "Weixin", "getParam", []);
	},

	getAccess_token : function(onSuccess, onError) {
		var self = this;
		self.getParam(function(params) {
			var url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + params.weixin_appid + "&secret=" + weixin_secret + "&code=" + self.code + "&grant_type=authorization_code";
			function callback(response) {
				var status = response.status, data = JSON.parse(response.data);
				if ('200' == status) {
					if (data.openid) {
						self.token.openid = data.openid || "none";
						self.token.access_token = data.access_token;
						onSuccess();
					} else {
						onError(data.errmsg);
					}
				} else {
					onError(response.error);
				}
			}
			self.__httpRequest(url, callback, onError)

		});
	},
	__httpRequest : function(url, onSuccess, onError) {
		cordovaHTTP.get(url, {}, {
			Authorization : "OAuth2: token"
		}, onSuccess, function(response) {
			onError(response.error)
		});
	},

	getUserInfo : function(callback, onError) {
		var self = this;
		self.getAccess_token(function() {
			self.__getUserInfo(callback, onError);
		}, onError);
	},

	__getUserInfo : function(onSuccess, onError) {
		var self = this;
		var url = "https://api.weixin.qq.com/sns/userinfo?access_token=" + self.token.access_token + "&openid=" + self.token.openid + "&lang=zh_CN";
		function callback(response) {
			var status = response.status, data = JSON.parse(response.data);
			if ('200' == status) {
				if (data.openid) {
					onSuccess(data);
				} else {
					onError(data.errmsg);
				}
			} else {
				onError(response.error);
			}
		}
		self.__httpRequest(url, callback, onError)
	},

	getAccessToken : function(onSuccess, onError) {
		var weixinPluginValue = localStorage.getItem('cordova.weixinPlugin');
		if (weixinPluginValue != null) {
			weixinPluginValue = JSON.parse(weixinPluginValue);
			if (new Date().getTime() / 1000 - weixinPluginValue.timeStamp / 1000 > 7100) {
				this.getRemoteAccessToken(onSuccess, onError);
			} else {
				if (onSuccess) {
					onSuccess.call(this, weixinPluginValue.accessToken);
				}
			}
		} else {
			this.getRemoteAccessToken(onSuccess, onError);
		}
	},
	getRemoteAccessToken : function(onSuccess, onError) {
		exec(function(accessToken) {
			var weixinPluginValue = {
				accessToken : accessToken,
				timeStamp : new Date().getTime()
			};
			localStorage.setItem('cordova.weixinPlugin', JSON.stringify(weixinPluginValue));
			if (onSuccess) {
				onSuccess(accessToken);
			}
		}, onError, "Weixin", "getAccessToken", []);
	},
	generatePrepayId : function(payInfo, onSuccess, onError) {
		if (payInfo.totalFee && typeof payInfo.totalFee !== "string") {
			payInfo.totalFee = payInfo.totalFee + "";
		}
		exec(onSuccess, onError, "Weixin", "generatePrepayId", [ payInfo ]);
	},
	sendPayReq : function(prepayId, onSuccess, onError) {
		exec(onSuccess, onError, "Weixin", "sendPayReq", [ {
			"prepayId" : prepayId
		} ]);
	},
	auth : function(onSuccess, onError) {
		this.ssoLogin(onSuccess, onError);
	},
	
	ssoLogin : function(onSuccess, onError) {
		var self = this;
		function codeSuccess(info) {
			self.code = info.code;
			onSuccess && onSuccess(info);
		}
		exec(codeSuccess, onError, "Weixin", "auth", []);
	},
};