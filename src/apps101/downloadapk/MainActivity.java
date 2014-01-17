/*
Copyright (c) 2014 
Lawrence Angrave
Rohan R. Arora

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
u
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */
package apps101.downloadapk;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * 
 * This Activity uses an AsyncTask to download an APK from the web to an SD Card
 * and install it.
 * 
 */
public class MainActivity extends Activity implements TextWatcher {
	public static final String TAG = "MainActivity";

	public static final boolean TEST_INSUFFICIENT_SPACE = false;

	private static final String FILE_NAME = "CourseraPeerGrading.apk";
	private File mRoot, mDir, mOutputFile;

	private Button mDownloadButton;
	private EditText mEditText;
	private URL mUrl;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mDownloadButton = (Button) findViewById(R.id.button_download);
		mEditText = (EditText) findViewById(R.id.edittext_url);
		mEditText.addTextChangedListener(this);

		// You can set a default URL to download here -
		mEditText.setText("");

		// We download the APK into a public readable directory - the external
		// storage area
		mRoot = Environment.getExternalStorageDirectory();
		mDir = new File(mRoot + "/CourseraAPK");
		mOutputFile = new File(mDir, FILE_NAME);

		// Some Basic Sanity Checks about our environment
		// If we don't have connectivity and a mounted, writeable SD Card then
		// display an error message and quit
		String error = checkStorage();
		if (error.length() > 0) {
			Toast.makeText(this, error, Toast.LENGTH_LONG).show();
			finish();
		}
		error = checkConnectivity();
		if (error.length() > 0) {
			Toast.makeText(this, error, Toast.LENGTH_LONG).show();
			finish();
		}
	}

	/**
	 * Helper method to check that the External storage is available
	 * 
	 * @return an empty String if the storage is available and writeable
	 *         otherwise returns a readable an error message
	 */
	private String checkStorage() {
		String state = Environment.getExternalStorageState();
		Log.d(TAG, "Storage:" + state);
		if (state.equals(Environment.MEDIA_MOUNTED))
			return "";
		if (state.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
			return "Storage (SD Card) available but read-only!";
		if (state.equals(Environment.MEDIA_REMOVED))
			return "No SD Card Storage; reconfigure your instance to include a 50 MB SD Card";
		return "Storage cannot be used:" + state
				+ "; try creating a new emulator with a 50MB SD Card";
	}

	/**
	 * Helper method to check that the system appears to be connected (and not
	 * in Airplane mode for example) Note this method does not actually try to
	 * perform any connections.
	 * 
	 * @return Returns an empty string if the emulator is configured to connect
	 *         other returns an error message.
	 */
	private String checkConnectivity() {
		ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = connManager.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnected())
			return "";
		return "No Internet Connectivity; Check your connections!";
	}

	/**
	 * Prepares the External storage (SD Card) and verifies that we can write to
	 * the desired directory. An existing file of the same name is deleted.
	 * 
	 * @return Returns an empty string if the storage is ready.
	 */
	private String prepareStorage() {
		try {
			Log.d(TAG, "Creating: " + mDir.getAbsolutePath());
			mDir.mkdirs(); // ignore return value, they may already exist

			mOutputFile.delete(); // may or may not already exist

			if (mOutputFile.exists())
				return "Could not delete existing file " + mOutputFile;

			if (!mOutputFile.createNewFile())
				return "Cannot create " + mOutputFile;
			mOutputFile.delete();

			StatFs stat = new StatFs(mRoot.getPath());
			if (stat.getAvailableBlocks() == 0) {
				return "SD Card Storage is full";
			}
			return "";
		} catch (Exception e) {
			Log.e(TAG, "Storage Error: ", e);
			return "Storage Error:" + e.getMessage();
		}
	}

	/**
	 * UI ethod that is called from the download button
	 * 
	 * @param view
	 */
	public void initializeDownload(View view) {

		if (mUrl.toString().length()> 0) {
			Log.i(TAG, "Downloading: " + mUrl);
			new DownloadAPK().execute(mUrl);
		}
	}

	/**
	 * Starts the installer activity to install the downloaded APK file.
	 */
	public void installAPK() {
		try {
			Log.d(TAG, "File to install: " + mOutputFile);
			Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(
					Uri.fromFile(mOutputFile),
					"application/vnd.android.package-archive");
			startActivity(install);
		} catch (Exception e) {
			Log.e(TAG, "Install Error", e);
		}
	}

	/**
	 * Async task to download and install the APK
	 * 
	 */
	public class DownloadAPK extends AsyncTask<URL, String, String> {
		private static final int BUFFER_SIZE = 8192;
		private ProgressDialog mDialog;

		protected void onPreExecute() {
			mDialog = new ProgressDialog(MainActivity.this);
			mDialog.show();
		}

		protected String doInBackground(URL... downloadURL) {
			String result = "Incomplete"; // Will be empty once the entire file
											// is downloaded
			int responseCode = 0; // Server response code (e.g. 200=OK;
									// 403=Forbidden;404=Not Found)
			InputStream input = null;
			OutputStream output = null;
			HttpURLConnection connection = null;
			int totalBytesDownloaded = 0;

			try {
				String storageReady = prepareStorage(); // Check storage is
														// still valid
				if (!storageReady.isEmpty())
					throw new Exception(storageReady);

				URL url = downloadURL[0];
				Log.d(TAG, "Opening: " + url);
				publishProgress("Opening " + url + " ...");
				connection = (HttpURLConnection) url.openConnection();
				connection.setConnectTimeout(15000);
				connection.connect();

				responseCode = connection.getResponseCode();
				publishProgress("Connected (" + responseCode + ")");

				Log.d(TAG, "Response Code:" + responseCode);

				// We connected, so try to open the connection stream and save
				// bytes to the SD Card
				input = new BufferedInputStream(url.openStream(), BUFFER_SIZE);
				output = new FileOutputStream(mOutputFile);

				if (TEST_INSUFFICIENT_SPACE) {
					publishProgress("Writing an infinite number of bytes...");
					while (true)
						output.write('a');
				}
				byte[] buffer = new byte[BUFFER_SIZE];

				long time = 0;
				int byteCount;

				while ((byteCount = input.read(buffer)) != -1) {
					if (isCancelled())
						throw new Exception("Cancelled");

					output.write(buffer, 0, byteCount);
					totalBytesDownloaded += byteCount;

					long t = SystemClock.uptimeMillis();
					if (t >= time) {
						publishProgress((totalBytesDownloaded / 1024)
								+ " KB ...");
						time = t + 500; // Limit progress updates to twice a
										// second
					}
				}
				publishProgress("Saving...");
				output.close();
				output = null; // Every byte has been saved
				result = ""; // Only now can we declare success!
				publishProgress("");
			} catch (Exception e) {
				Log.e(TAG, "Exception: ", e);
				mOutputFile.delete(); // Delete the partially complete file
				result = responseCode + ":" + e.getMessage();
			} finally {
				try {
					if (output != null) {
						output.close();
						output = null;
					}

					if (input != null) {
						input.close();
						input = null;
					}
					if (connection != null) {
						connection.disconnect();
					}
				} catch (IOException e) {
					Log.d(TAG, "Exception closing streams (ignored)", e);
				}
			}
			Log.d(TAG, "Total Bytes Downloaded: " + totalBytesDownloaded);
			return result;
		}

		/**
		 * Called on the UI Thread with whatever value we passed into
		 * publishProgress We'll use it to update our dialog message
		 */
		protected void onProgressUpdate(String... values) {
			if (values.length > 0 && values[0] != null && mDialog.isShowing())
				mDialog.setMessage(values[0]);
			super.onProgressUpdate(values);
		}

		/**
		 * Call on the UI Thread once the background thread has finished. Now
		 * the dust has settled we can install the file (or not if there was an
		 * error)
		 */
		protected void onPostExecute(String error) {
			if (mDialog.isShowing())
				mDialog.dismiss();

			if (error.isEmpty()) {
				Log.d(TAG, "Installing APK");
				MainActivity.this.installAPK();
			} else {
				Log.e(TAG, error);
				Toast.makeText(getApplicationContext(),
						"Download error:" + error, Toast.LENGTH_LONG).show();

			}
		}
	}

	// TextWatcher interface
	@Override
	public void afterTextChanged(Editable s) {
		boolean valid = false;
		String address = mEditText.getText().toString().trim();
		if (!address.isEmpty()) {
			if (address.indexOf(':') == -1)
				address = "http://" + address;
			try {
				mUrl = new URL(address);
				valid = true;
			} catch (MalformedURLException e) {
			}
		}
		mDownloadButton.setEnabled(valid);
	}

	// TextWatcher interface
	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	// TextWatcher interface
	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
	}

}
