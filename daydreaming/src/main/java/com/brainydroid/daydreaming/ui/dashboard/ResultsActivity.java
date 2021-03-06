package com.brainydroid.daydreaming.ui.dashboard;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.brainydroid.daydreaming.R;
import com.brainydroid.daydreaming.background.ErrorHandler;
import com.brainydroid.daydreaming.background.Logger;
import com.brainydroid.daydreaming.background.StatusManager;
import com.brainydroid.daydreaming.db.ParametersStorage;
import com.brainydroid.daydreaming.db.ProfileStorage;
import com.brainydroid.daydreaming.db.ResultsStorage;
import com.brainydroid.daydreaming.network.CryptoStorage;
import com.brainydroid.daydreaming.network.HttpConversationCallback;
import com.brainydroid.daydreaming.network.ServerTalker;
import com.google.inject.Inject;

import java.util.HashMap;

import roboguice.activity.RoboFragmentActivity;
import roboguice.inject.ContentView;
import roboguice.inject.InjectView;

@ContentView(R.layout.activity_results)
public class ResultsActivity extends RoboFragmentActivity {

    public static String TAG = "ResultsActivity";

    public static String DOWNLOAD_RESULTS = "loadResults";

    @Inject ParametersStorage parametersStorage;
    @Inject StatusManager statusManager;
    @Inject ServerTalker serverTalker;
    @Inject CryptoStorage cryptoStorage;
    @Inject ProfileStorage profileStorage;
    @Inject ResultsStorage resultsStorage;
    @Inject ErrorHandler errorHandler;
    @Inject Activity activity;
    @InjectView(R.id.activity_results_webView) private WebView webView;
    @InjectView(R.id.activity_results_root) private LinearLayout rootView;

    private boolean failedOnceAlready = false;
    private boolean resultsDownloaded = false;
    private boolean pageLoaded = false;
    private ProgressDialog progressDialog;
    private JSResults jsResults = null;

    public static class JSResults {

        private int versionCode;
        private String resultsWrap;

        public JSResults(int versionCode, String resultsWrap) {
            this.versionCode = versionCode;
            this.resultsWrap = resultsWrap;
        }

        @JavascriptInterface
        public int getVersionCode() {
            return versionCode;
        }

        @JavascriptInterface
        public String getResultsWrap() {
            return resultsWrap;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.v(TAG, "Creating");

        super.onCreate(savedInstanceState);

        // No need to notify results again, the user opened them
        statusManager.set(StatusManager.ARE_RESULTS_NOTIFIED);
        statusManager.set(StatusManager.ARE_RESULTS_NOTIFIED_DASHBOARD);

        if (getIntent().getBooleanExtra(DOWNLOAD_RESULTS, true)) {
            resultsDownloaded = false;
            pageLoaded = false;
        } else {
            setResultsDownloaded();
        }
        webView.getSettings().setJavaScriptEnabled(true);
        loadResultsAndWebView();
    }

    public void onResume() {
        Logger.v(TAG, "Resuming");
        super.onResume();
        webView.onResume();
    }

    public void onPause() {
        Logger.v(TAG, "Pausing");
        super.onPause();
        webView.onPause();
    }

    private void setPageLoaded() {
        pageLoaded = true;
        finalizeIfLoadingFinished();
    }

    private void setResultsDownloaded() {
        resultsDownloaded = true;
        finalizeIfLoadingFinished();
    }

    private void finalizeIfLoadingFinished() {
        if (pageLoaded && resultsDownloaded) {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            // Run on UI thread
            rootView.post(new Runnable() {
                @Override
                public void run() {
                    webView.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    public void loadResultsAndWebView() {
        Logger.d(TAG, "Starting results retrieval from server");

        progressDialog = ProgressDialog.show(activity,
                "Results", "Loading your results...");

        if (!resultsDownloaded) {
            Logger.v(TAG, "Results not downloaded -> downloading");

            HttpConversationCallback resultsCallback = new HttpConversationCallback() {
                private String TAG = "getResults HttpConversationCallback";

                @Override
                public void onHttpConversationFinished(boolean success, String serverAnswer) {
                    if (success) {
                        Logger.i(TAG, "Results successfully retrieved");

                        setResultsDownloaded();

                        // Cache results
                        if (!resultsStorage.saveResults(serverAnswer)) {
                            Logger.e(TAG, "Couldn't save results to file");
                            toastOnUIThread("Could not cache your results locally! " +
                                            "You'll have to download them again next time",
                                    Toast.LENGTH_LONG);
                        }

                        jsResults = new JSResults(profileStorage.getAppVersionCode(), serverAnswer);
                        launchWebView();
                    } else {
                        Logger.i(TAG, "Failed to get results");
                        toastOnUIThread("Oh no! There was an error loading the results",
                                Toast.LENGTH_SHORT);
                        progressDialog.dismiss();
                        onBackPressed();
                    }
                }
            };

            HashMap<String, String> args = new HashMap<String, String>();
            args.put("access", "private");

            serverTalker.authenticatedGet(serverTalker.getResultsUrl(), args, resultsCallback);
        } else {
            Logger.v(TAG, "Results downloaded -> loading from cache");

            (new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    Logger.d(TAG, "Loading results from disk");
                    return resultsStorage.getResults();
                }

                @Override
                protected void onPostExecute(String resultsString) {
                    if (resultsString == null) {
                        Logger.e(TAG, "Could not load results from file");
                        if (!failedOnceAlready) {
                            Logger.i(TAG, "Relaunching results download");
                            failedOnceAlready = true;
                            toastOnUIThread("Could not load your results from cache! " +
                                    "Downloading them from server", Toast.LENGTH_LONG);
                            resultsDownloaded = false;
                            loadResultsAndWebView();
                        } else {
                            Logger.e(TAG, "This error already happened, aborting");
                            // We already went through this. Do not recurse.
                            toastOnUIThread("Sorry! There was problem loading your results, " +
                                    "developers have been notified", Toast.LENGTH_LONG);
                            errorHandler.logError("Could not load results, twice",
                                    new Exception("Could not load results, twice"));
                            if (progressDialog != null) {
                                progressDialog.dismiss();
                            }
                            activity.finish();
                        }
                    } else {
                        jsResults = new JSResults(profileStorage.getAppVersionCode(), resultsString);
                        launchWebView();
                    }
                }
            }).execute();
        }
    }

    private void launchWebView() {
        // Load the results in the webView and start it
        // Run this on UI thread
        rootView.post(new Runnable() {
            @Override
            public void run() {
                webView.clearCache(true);
                webView.addJavascriptInterface(jsResults, "injectedResults");
                webView.setWebViewClient(new WebViewClient() {

                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                    }

                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        setPageLoaded();
                    }

                    public void onReceivedError(WebView view, int errorCode,
                                                String description, String failingUrl) {
                        progressDialog.dismiss();
                        toastOnUIThread("Oh no! " + description, Toast.LENGTH_SHORT);
                    }
                });
            }
        });

        CryptoStorage.AuthTokenCallback authTokenCallback = new CryptoStorage.AuthTokenCallback() {
            private String TAG = "launchWebView AuthTokenCallback";
            @Override
            public void onAuthTokenReady(String authToken) {
                if (authToken != null) {
                    Logger.d(TAG, "AuthToken successfully created. Launching GET.");

                    // Build the url with query arguments
                    String tmpAuthenticatedResultsUrl = parametersStorage.getResultsPageUrl();
                    if (tmpAuthenticatedResultsUrl.contains("?")) {
                        tmpAuthenticatedResultsUrl += "&";
                    } else {
                        tmpAuthenticatedResultsUrl += "?";
                    }
                    tmpAuthenticatedResultsUrl += "auth_token=" + authToken;
                    final String authenticatedResultsUrl = tmpAuthenticatedResultsUrl;
                    // Run on UI thread
                    rootView.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.loadUrl(authenticatedResultsUrl);
                        }
                    });
                } else {
                    Logger.e(TAG, "Error retrieving auth token");
                    progressDialog.dismiss();
                    // Run on UI thread
                    toastOnUIThread("Could not retrieve your results! Please Try again later",
                            Toast.LENGTH_SHORT);
                }
            }
        };

        cryptoStorage.createJwsAuthToken(authTokenCallback);
    }

    private void toastOnUIThread(final String text, final int duration) {
        // Run on UI thread
        rootView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, text, duration).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        Logger.v(TAG, "Back pressed, slide transition");
        super.onBackPressed();
        overridePendingTransition(R.anim.push_right_in, R.anim.push_right_out);
    }
}
