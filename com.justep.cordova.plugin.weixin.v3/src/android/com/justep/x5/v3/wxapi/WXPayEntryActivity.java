package com.justep.x5.v3.wxapi;


import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

import com.justep.cordova.Config;
import com.tencent.mm.opensdk.constants.ConstantsAPI;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.justep.cordova.plugin.weixin.WeixinV3;

import java.util.List;

public class WXPayEntryActivity extends Activity implements IWXAPIEventHandler {

  private static final String TAG = "Weixin";
	
    private IWXAPI api;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Config.init(this);
        String appid = Config.getPreferences().getString("weixin_appid", "");
    	api = WXAPIFactory.createWXAPI(this, appid);
        api.handleIntent(getIntent(), this);
    }

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
        api.handleIntent(intent, this);
	}

	@Override
	public void onReq(BaseReq req) {
	}

	@Override
	public void onResp(BaseResp resp) {
		Log.d(TAG, "onPayFinish, errCode = " + resp.errCode);
		if (resp.getType() == ConstantsAPI.COMMAND_PAY_BY_WX) {
			Intent intent;
		    try {
		        Intent resolveIntent = new Intent(Intent.ACTION_MAIN, null);
		        resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		        resolveIntent.setPackage(getPackageName());

		        PackageManager pm = this.getPackageManager();
		        List<ResolveInfo> apps = pm.queryIntentActivities(resolveIntent, 0);
		        ResolveInfo ri = apps.iterator().next();
		        if (ri != null) {
		          String startappName = ri.activityInfo.packageName;
		          String className = ri.activityInfo.name;
		          System.out.println("启动的activity是: " + startappName + ":" + className);
		          intent = new Intent(this, WXPayEntryActivity.class.getClassLoader().loadClass(className));
		          Bundle bundle = new Bundle();
		          bundle.putInt("weixinPayRespCode", resp.errCode);
		          bundle.putString("intentType", "com.justep.cordova.plugin.weixin.Weixin");
		          intent.putExtras(bundle);
		          //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		          WeixinV3.instance.onWeixinResp(intent);
		          //Log.i(TAG, "startActivity");
		          //startActivity(intent);
		          finish();
		        }
			} catch (ClassNotFoundException e) {
			   e.printStackTrace();
			}
	    }
	}
}
