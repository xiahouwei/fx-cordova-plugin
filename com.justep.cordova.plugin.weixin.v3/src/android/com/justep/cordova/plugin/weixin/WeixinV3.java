package com.justep.cordova.plugin.weixin;

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.sourceforge.simcpux.MD5;
import net.sourceforge.simcpux.Util;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;
import android.view.View;

import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.modelmsg.WXTextObject;
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject;
import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

public class WeixinV3 extends CordovaPlugin {
	public static final String TAG = "Weixin";

	public static final String ERROR_WX_NOT_INSTALLED = "未安装微信";
	public static final String ERROR_ARGUMENTS = "参数错误";

	public static final String ERROR_INVALID_PARAMETERS = "参数格式错误";
	public static final String ERROR_SEND_REQUEST_FAILED = "发送请求失败";
	public static final String ERROR_WECHAT_RESPONSE_COMMON = "普通错误";
	public static final String ERROR_WECHAT_RESPONSE_USER_CANCEL = "用户点击取消并返回";
	public static final String ERROR_WECHAT_RESPONSE_SENT_FAILED = "发送失败";
	public static final String ERROR_WECHAT_RESPONSE_AUTH_DENIED = "授权失败";
	public static final String ERROR_WECHAT_RESPONSE_UNSUPPORT = "微信不支持";
	public static final String ERROR_WECHAT_RESPONSE_UNKNOWN = "未知错误";

	public static final String KEY_ARG_MESSAGE = "message";
	public static final String KEY_ARG_SCENE = "scene";
	public static final String KEY_ARG_MESSAGE_TITLE = "title";
	public static final String KEY_ARG_MESSAGE_DESCRIPTION = "description";
	public static final String KEY_ARG_MESSAGE_THUMB = "thumb";
	public static final String KEY_ARG_MESSAGE_MEDIA = "media";
	public static final String KEY_ARG_MESSAGE_MEDIA_TYPE = "type";
	public static final String KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL = "webpageUrl";
	public static final String KEY_ARG_MESSAGE_MEDIA_TEXT = "text";
	public static final String KEY_ARG_MINIDATA = "minidata";
	public static final String KEY_ARG_MINIPATH = "path";
	public static final String KEY_ARG_MINIUSERNAME = "userName";
	// 拉取的小程序类型 addby shw 2019-10-09
	public static final String KEY_ARG_MINITYPE = "type";
	
	public static final int TYPE_WX_SHARING_APP = 1;
	public static final int TYPE_WX_SHARING_EMOTION = 2;
	public static final int TYPE_WX_SHARING_FILE = 3;
	public static final int TYPE_WX_SHARING_IMAGE = 4;
	public static final int TYPE_WX_SHARING_MUSIC = 5;
	public static final int TYPE_WX_SHARING_VIDEO = 6;
	public static final int TYPE_WX_SHARING_WEBPAGE = 7;
	public static final int TYPE_WX_SHARING_TEXT = 8;

	public static IWXAPI api;

	public static WeixinV3 instance;

	protected static CallbackContext currentCallbackContext;

	private static String app_id;
	private static String app_secret;
	private static String partner_id;
	private static String api_key;
	private HashMap<String, PayOrder> payOrderList = new HashMap<String, PayOrder>();

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		// save the current callback context
		currentCallbackContext = callbackContext;
		// check if installed
		if (!api.isWXAppInstalled()) {
			callbackContext.error(ERROR_WX_NOT_INSTALLED);
			return true;
		}
		if (action.equals("share")) {
			// sharing
			return share(args, callbackContext);
			
		} else if (action.equals("LaunchMini")) {
			
			return LaunchMini(args, callbackContext);
			
		} else if (action.equals("generatePrepayId")) {
			return generatePrepayId(args);
		} else if (action.equals("sendPayReq")) {
			return sendPayReq(args);
		} else if (action.equals("auth")) {
			return sendAuthRequest(args);
		} else if (action.equals("getParam")) {
			return getParam();
		}
		return false;
	}

	protected boolean sendAuthRequest(JSONArray args) {// 回调了才有用户名称
		Log.d("Weixin", "sendAuthRequest sendAuthRequest  ");
		final SendAuth.Req req = new SendAuth.Req();
		req.scope = "snsapi_userinfo";
		req.state = "sendauthrequrest";
		Boolean sendSuccess = api.sendReq(req);
		if (!sendSuccess) {
			currentCallbackContext.error("发送请求失败");
		}
		return true;
	}

	protected boolean getParam() {// 回调了才有用户名称
		Log.d("Weixin", "getParam ");
		try {
			JSONObject params = new JSONObject();
			params.put("weixin_appid", app_id);
			params.put("weixin_secret", app_secret);
			params.put("weixin_partner_id", partner_id);
			currentCallbackContext.success(params);
		} catch (JSONException e) {
			return false;
		}
		return true;
	}

	private boolean generatePrepayId(JSONArray args) {
		// pay
		try {
			JSONObject prepayInfo = args.getJSONObject(0);
			String packageParams = genProductArgs(prepayInfo);
			new GetPrepayIdTask(packageParams).execute();
		} catch (JSONException e) {
			currentCallbackContext.error("参数格式不正确");
			return false;
		}
		return true;
	}

	protected boolean sendPayReq(JSONArray args) {
		Log.i(TAG, "pay begin");
		try {
			JSONObject prepayIdObj = args.getJSONObject(0);
			String prepayId = prepayIdObj.getString("prepayId");
			sendPayReq(prepayId);
		} catch (JSONException e) {
			e.printStackTrace();
			currentCallbackContext.error("参数错误");
			return false;
		}
		return true;
	}

	public void getWXAPI() {
		if (api == null) {
			api = WXAPIFactory.createWXAPI(webView.getContext(), app_id, true);
			api.registerApp(app_id);
		}
	}

	public String checkArgs(WXMediaMessage var1) {
		if((var1.thumbData != null) && (var1.thumbData.length > 32768)) {
			return "缩略图尺寸不得超过32k";
		}

		if((var1.title != null) && (var1.title.length() > 512)) {
			return "title长度不得超过512字节";
		}

		if((var1.description != null) && (var1.description.length() > 1024)) {
			return "description长度不得超过1024字节";
		}

		if(var1.mediaObject == null) {
		  return "mediaObject不能为空";
		}

		if((var1.mediaTagName != null) && (var1.mediaTagName.length() > 64)) {
			return "mediaTagName长度不得超过64字节";
		}

		if((var1.messageAction != null) && (var1.messageAction.length() > 2048)) {
			return "messageAction长度不得超过2048字节";
		}

		if ((var1.messageExt != null) && (var1.messageExt.length() > 2048)) {
		  return "messageExt长度不得超过2048字节";
		}
		return null;
	}
	
	//打开小程序(success)
	protected boolean LaunchMini(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// check if # of arguments is correct
		if (args.length() != 1) {
			callbackContext.error(ERROR_ARGUMENTS);
		}
		final JSONObject params = args.getJSONObject(0);
		WXLaunchMiniProgram.Req req = new WXLaunchMiniProgram.Req();
		// 填小程序原始id
		req.userName = params.getString(KEY_ARG_MINIUSERNAME);
		//拉起小程序页面的可带参路径，不填默认拉起小程序首页
		req.path = params.getString(KEY_ARG_MINIPATH);
		// addby shw 2019-10-09
		String type = params.getString(KEY_ARG_MINITYPE);
		if (type.equals("0")) {
			// 可选打开 正式版
			req.miniprogramType = WXLaunchMiniProgram.Req.MINIPTOGRAM_TYPE_RELEASE;
		} else if (type.equals("1")) {
			// 可选打开 开发版
			req.miniprogramType = WXLaunchMiniProgram.Req.MINIPROGRAM_TYPE_TEST;
		} else if (type.equals("2")) {
			// 可选打开 体验版
			req.miniprogramType = WXLaunchMiniProgram.Req.MINIPROGRAM_TYPE_PREVIEW;
		}
		api.sendReq(req);
		return true;
	}
	
	
	
	protected boolean share(JSONArray args, CallbackContext callbackContext) throws JSONException {
		// check if # of arguments is correct
		if (args.length() != 1) {
			callbackContext.error(ERROR_ARGUMENTS);
		}

		final JSONObject params = args.getJSONObject(0);
		final SendMessageToWX.Req req = new SendMessageToWX.Req();
		req.transaction = String.valueOf(System.currentTimeMillis());

		if (params.has(KEY_ARG_SCENE)) {
			req.scene = params.getInt(KEY_ARG_SCENE);
		} else {
			req.scene = SendMessageToWX.Req.WXSceneTimeline;
		}

		// run in background
		cordova.getThreadPool().execute(new Runnable() {
			@Override
			public void run() {
				try {
					req.message = buildSharingMessage(params.getJSONObject(KEY_ARG_MESSAGE));
					String errInfo = checkArgs(req.message);
					if(errInfo != null){
						currentCallbackContext.error(errInfo);
						return;
					}
				} catch (JSONException e) {
					e.printStackTrace();
					currentCallbackContext.error(e.getMessage());
				}

				Boolean sended = api.sendReq(req);
				if (sended) {
					// currentCallbackContext.success();
				} else {
					currentCallbackContext.error("发送失败");
				}
			}
		});
		return true;
	}

	/* 图片压缩方法一
   *
   * 计算 bitmap大小，如果超过31kb，则进行压缩
   *
   * @param bitmap
   * @return
   */
  private Bitmap ImageCompressL(Bitmap bitmap) {
	double targetwidth = Math.sqrt(31.00 * 1000);
	if (bitmap.getWidth() > targetwidth || bitmap.getHeight() > targetwidth) {
	  // 创建操作图片用的matrix对象
	  Matrix matrix = new Matrix();
	  //计算宽高缩放率
	  double x = Math.min(targetwidth / bitmap.getWidth(), targetwidth / bitmap.getHeight());
	  //缩放图片动作
	  matrix.postScale((float) x, (float) x);
	  bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),  bitmap.getHeight(), matrix, true);
	}
	return bitmap;
  }


	protected WXMediaMessage buildSharingMessage(JSONObject message) throws JSONException {
		URL thumbnailUrl = null;
		Bitmap thumbnail = null;

		try {
			thumbnailUrl = new URL(message.getString(KEY_ARG_MESSAGE_THUMB));
			thumbnail = BitmapFactory.decodeStream(thumbnailUrl.openConnection().getInputStream());
	  if(thumbnail != null){
		thumbnail = ImageCompressL(thumbnail);
	  }
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		WXMediaMessage wxMediaMessage = new WXMediaMessage();
		wxMediaMessage.title = message.getString(KEY_ARG_MESSAGE_TITLE);
		wxMediaMessage.description = message.getString(KEY_ARG_MESSAGE_DESCRIPTION);
		if (thumbnail != null) {
			wxMediaMessage.setThumbImage(thumbnail);
		}

		// media parameters
		WXMediaMessage.IMediaObject mediaObject = null;
		JSONObject media = message.getJSONObject(KEY_ARG_MESSAGE_MEDIA);

		// check types
		int type = media.has(KEY_ARG_MESSAGE_MEDIA_TYPE) ? media.getInt(KEY_ARG_MESSAGE_MEDIA_TYPE) : TYPE_WX_SHARING_WEBPAGE;
		switch (type) {
		case TYPE_WX_SHARING_APP:
			break;

		case TYPE_WX_SHARING_EMOTION:
			break;

		case TYPE_WX_SHARING_FILE:
			break;

		case TYPE_WX_SHARING_IMAGE:
			break;

		case TYPE_WX_SHARING_MUSIC:
			break;

		case TYPE_WX_SHARING_VIDEO:
			break;

		case TYPE_WX_SHARING_TEXT:
			mediaObject = new WXTextObject();
			((WXTextObject) mediaObject).text = media.getString(KEY_ARG_MESSAGE_MEDIA_TEXT);
			break;

		case TYPE_WX_SHARING_WEBPAGE:
		default:
			mediaObject = new WXWebpageObject();
			((WXWebpageObject) mediaObject).webpageUrl = media.getString(KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL);
		}
		wxMediaMessage.mediaObject = mediaObject;
		return wxMediaMessage;
	}

	private String genProductArgs(JSONObject args) {
		try {
			String nonceStr = genNonceStr();
			List<NameValuePair> packageParams = new LinkedList<NameValuePair>();
			packageParams.add(new BasicNameValuePair("appid", app_id));
			packageParams.add(new BasicNameValuePair("body", args.getString("body")));
			packageParams.add(new BasicNameValuePair("mch_id", partner_id));
			packageParams.add(new BasicNameValuePair("nonce_str", nonceStr));
			packageParams.add(new BasicNameValuePair("notify_url", args.getString("notifyUrl")));
			packageParams.add(new BasicNameValuePair("out_trade_no", args.getString("tradeNo")));
			packageParams.add(new BasicNameValuePair("spbill_create_ip", Util.getIpAddress()));
			packageParams.add(new BasicNameValuePair("total_fee", args.getString("totalFee")));
			packageParams.add(new BasicNameValuePair("trade_type", "APP"));

			String sign = genPackageSign(packageParams);
			packageParams.add(new BasicNameValuePair("sign", sign));
			String xmlstring = toXml(packageParams);
			return new String(xmlstring.toString().getBytes(), "ISO8859-1");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private String toXml(List<NameValuePair> params) {
		StringBuilder sb = new StringBuilder();
		sb.append("<xml>");
		for (int i = 0; i < params.size(); i++) {
			sb.append("<" + params.get(i).getName() + ">");

			sb.append(params.get(i).getValue());
			sb.append("</" + params.get(i).getName() + ">");
		}
		sb.append("</xml>");

		Log.e("orion", sb.toString());
		return sb.toString();
	}

	private class GetPrepayIdTask extends AsyncTask<Void, Void, Map<String, String>> {
		String packageParams;

		public GetPrepayIdTask(String packageParams) {
			this.packageParams = packageParams;
		}

		@Override
		protected void onPostExecute(Map<String, String> result) {
			Toast.makeText(cordova.getActivity(), "正在生成预支付订单", Toast.LENGTH_LONG).show();
			String prepayId = result.get("prepay_id");
			if (prepayId != null && !prepayId.equals("")) {
				currentCallbackContext.success(prepayId);
			} else {
				currentCallbackContext.error(result.get("return_code"));
			}
		}

		@Override
		protected void onPreExecute() {
			Log.i(TAG, "正在获取订单id");
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

		@Override
		protected Map<String, String> doInBackground(Void... params) {
			String url = String.format("https://api.mch.weixin.qq.com/pay/unifiedorder");
			byte[] buf = Util.httpPost(url, packageParams);
			String content = new String(buf);
			Map<String, String> xml = decodeXml(content);
			return xml;
		}
	}

	public Map<String, String> decodeXml(String content) {
		try {
			Map<String, String> xml = new HashMap<String, String>();
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(new StringReader(content));
			int event = parser.getEventType();
			while (event != XmlPullParser.END_DOCUMENT) {

				String nodeName = parser.getName();
				switch (event) {
				case XmlPullParser.START_DOCUMENT:

					break;
				case XmlPullParser.START_TAG:

					if ("xml".equals(nodeName) == false) {
						// 实例化student对象
						xml.put(nodeName, parser.nextText());
					}
					break;
				case XmlPullParser.END_TAG:
					break;
				}
				event = parser.next();
			}

			return xml;
		} catch (Exception e) {
			currentCallbackContext.error("获取与支付订单失败！");
		}
		return null;

	}

	private String genNonceStr() {
		Random random = new Random();
		return MD5.getMessageDigest(String.valueOf(random.nextInt(10000)).getBytes());
	}

	private long genTimeStamp() {
		return System.currentTimeMillis() / 1000;
	}

	private String genPackageSign(List<NameValuePair> params) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < params.size(); i++) {
			sb.append(params.get(i).getName());
			sb.append('=');
			sb.append(params.get(i).getValue());
			sb.append('&');
		}
		sb.append("key=");
		sb.append(api_key);

		String packageSign = MD5.getMessageDigest(sb.toString().getBytes(Charset.forName("utf-8"))).toUpperCase();

		return packageSign;
	}

	private String genAppSign(List<NameValuePair> params) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < params.size(); i++) {
			sb.append(params.get(i).getName());
			sb.append('=');
			sb.append(params.get(i).getValue());
			sb.append('&');
		}
		sb.append("key=");
		sb.append(api_key);

		String appSign = MD5.getMessageDigest(sb.toString().getBytes()).toUpperCase();
		return appSign;
	}

	private PayReq genPayReq(String prepay_id) {
		PayReq req = new PayReq();
		req.appId = app_id;
		req.partnerId = partner_id;
		req.prepayId = prepay_id;
		req.packageValue = "Sign=WXPay";
		req.nonceStr = genNonceStr();
		req.timeStamp = String.valueOf(genTimeStamp());

		List<NameValuePair> signParams = new LinkedList<NameValuePair>();
		signParams.add(new BasicNameValuePair("appid", req.appId));
		signParams.add(new BasicNameValuePair("noncestr", req.nonceStr));
		signParams.add(new BasicNameValuePair("package", req.packageValue));
		signParams.add(new BasicNameValuePair("partnerid", req.partnerId));
		signParams.add(new BasicNameValuePair("prepayid", req.prepayId));
		signParams.add(new BasicNameValuePair("timestamp", req.timeStamp));

		req.sign = genAppSign(signParams);
		return req;
	}

	private void sendPayReq(String prepayId) {
		api.registerApp(app_id);

		final PayReq req = genPayReq(prepayId);
		cordova.getThreadPool().execute(new Runnable() {
			@Override
			public void run() {
				Boolean sended = api.sendReq(req);
				if (!sended) {
					currentCallbackContext.error("发送支付请求失败");
				}
			}
		});
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		instance = this;
		app_id = webView.getPreferences().getString("weixin_appid", "");
		app_secret = webView.getPreferences().getString("weixin_secret", "");
		partner_id = webView.getPreferences().getString("weixin_partner_id", "");
		api_key = webView.getPreferences().getString("weixin_api_key", "");
		getWXAPI();
		this.onWeixinResp(cordova.getActivity().getIntent());
	}

	public void onWeixinResp(Intent intent) {
		Bundle extras = intent.getExtras();
		if (extras != null) {
			String intentType = extras.getString("intentType");
			if ("com.justep.cordova.plugin.weixin.Weixin".equals(intentType)) {
				if (currentCallbackContext != null) {
					currentCallbackContext.success(extras.getInt("weixinPayRespCode"));
				}
			}
		}
	}

	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Log.i(TAG, "onNewIntent");
		this.onWeixinResp(intent);
	}

	public CallbackContext getCurrentCallbackContext() {
		return currentCallbackContext;
	}
}
