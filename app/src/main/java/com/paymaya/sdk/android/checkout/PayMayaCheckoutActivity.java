package com.paymaya.sdk.android.checkout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.paymaya.sdk.android.BuildConfig;
import com.paymaya.sdk.android.PayMaya;
import com.paymaya.sdk.android.R;
import com.paymaya.sdk.android.common.utils.JSONUtils;
import com.paymaya.sdk.android.common.utils.Preconditions;
import com.paymaya.sdk.android.checkout.models.Checkout;
import com.paymaya.sdk.android.checkout.models.RedirectUrl;
import com.paymaya.sdk.android.common.network.AndroidClient;
import com.paymaya.sdk.android.common.network.Request;
import com.paymaya.sdk.android.common.network.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jadeantolingaa on 10/28/15.
 */
public final class PayMayaCheckoutActivity extends Activity {

    public static final int RESULT_FAILURE = 1063;

    public static final String EXTRAS_CLIENT_KEY = "extras_client_key";
    public static final String EXTRAS_CLIENT_SECRET = "extras_client_secret";
    public static final String EXTRAS_CHECKOUT = "extras_checkout";
    public static final String EXTRAS_CHECKOUT_BUNDLE = "extras_bundle";

    private Checkout mCheckout;

    private String mClientKey;

    private WebView mWebView;
    private ProgressBar mProgressBar;
    private String mSessionRedirectUrl;
    private String mSessionCheckoutId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.paymaya_checkout_activity);

        Intent intent = getIntent();
        Preconditions.checkNotNull(intent, "Missing intent.");

        Bundle bundle = intent.getBundleExtra(EXTRAS_CHECKOUT_BUNDLE);
        Preconditions.checkNotNull(bundle, "Missing bundle.");

        mCheckout = bundle.getParcelable(EXTRAS_CHECKOUT);
        Preconditions.checkNotNull(mCheckout, "Missing checkout object.");

        mClientKey = intent.getStringExtra(EXTRAS_CLIENT_KEY);
        Preconditions.checkNotNull(mClientKey, "Missing client key.");

        initialize();
        requestCreateCheckout();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initialize() {
        mSessionRedirectUrl = "";
        mProgressBar = (ProgressBar) findViewById(R.id.paymaya_checkout_activity_progress_bar);
        mWebView = (WebView) findViewById(R.id.paymaya_checkout_activity_web_view);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setAllowFileAccess(true);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                RedirectUrl redirectUrl = mCheckout.getRedirectUrl();

                if (url.startsWith(redirectUrl.getSuccessUrl())) {
                    finishSuccess();
                    return true;
                } else if (url.startsWith(redirectUrl.getCancelUrl())) {
                    finishCanceled();
                    return true;
                } else if (url.startsWith(redirectUrl.getFailureUrl())) {
                    finishFailure();
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.contains(mSessionCheckoutId)) {
                    hideProgress();
                }

                super.onPageFinished(view, url);
            }
        });
    }

    private void requestCreateCheckout() {
        new AsyncTask<Void, Void, Response>() {
            @Override
            protected Response doInBackground(Void... voids) {
                try {
                    Request request = new Request(Request.Method.POST, PayMaya.getEnvironment()
                            == PayMaya.ENVIRONMENT_PRODUCTION ?
                            BuildConfig.API_CHECKOUT_ENDPOINT_PRODUCTION :
                            BuildConfig.API_CHECKOUT_ENDPOINT_SANDBOX + "/checkouts");

                    byte[] body = JSONUtils.toJSON(mCheckout).toString().getBytes();
                    request.setBody(body);

                    String key = mClientKey + ":";
                    String authorization = Base64.encodeToString(key.getBytes(), Base64.DEFAULT);

                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Authorization", "Basic " + authorization);
                    headers.put("Content-Length", Integer.toString(body.length));
                    request.setHeaders(headers);

                    AndroidClient androidClient = new AndroidClient();
                    return androidClient.call(request);
                } catch (JSONException e) {
                    return new Response(-1, "");
                }
            }

            @Override
            protected void onPostExecute(Response response) {
                if (response.getCode() == 200) {
                    try {
                        JSONObject responseBody = new JSONObject(response.getResponse());
                        mSessionRedirectUrl = responseBody.getString("redirectUrl");
                        String[] redirectUrlParts = mSessionRedirectUrl.split("\\?");
                        mSessionCheckoutId = redirectUrlParts[redirectUrlParts.length - 1];
                        loadUrl(mSessionRedirectUrl);
                    } catch (JSONException e) {
                        finishFailure();
                    }
                } else {
                    finishFailure();
                }
            }
        }.execute();
    }

    private void finishSuccess() {
        Intent intent = new Intent();
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    private void finishCanceled() {
        Intent intent = new Intent();
        setResult(Activity.RESULT_CANCELED, intent);
        finish();
    }

    private void finishFailure() {
        Intent intent = new Intent();
        setResult(RESULT_FAILURE, intent);
        finish();
    }

    private void loadUrl(String url) {
        mWebView.loadUrl(url);
    }

    public void showProgress() {
        mProgressBar.setVisibility(View.VISIBLE);
    }

    public void hideProgress() {
        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}