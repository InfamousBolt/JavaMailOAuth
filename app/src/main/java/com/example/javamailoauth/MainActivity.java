package com.example.javamailoauth;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;


import com.example.javamailoauth.helper.InternetDetector;
import com.example.javamailoauth.helper.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.snackbar.Snackbar;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.example.javamailoauth.helper.InternetDetector;
import com.example.javamailoauth.helper.Utils;

public class MainActivity extends AppCompatActivity {

    FloatingActionButton sendFabButton;
    EditText edtToAddress, edtSubject, edtMessage, edtAttachmentData;
    Toolbar toolbar;
    GoogleAccountCredential mCredential;
    ProgressDialog mProgress;
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {
            GmailScopes.GMAIL_LABELS,
            GmailScopes.GMAIL_COMPOSE,
            GmailScopes.GMAIL_INSERT,
            GmailScopes.GMAIL_MODIFY,
            GmailScopes.GMAIL_READONLY,
            GmailScopes.MAIL_GOOGLE_COM
    };
    private InternetDetector internetDetector;
    private final int SELECT_PHOTO = 1;
    public String fileName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();

        findViewById(R.id.attachment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.checkPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    photoPickerIntent.setType("image/*");
                    startActivityForResult(photoPickerIntent, SELECT_PHOTO);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SELECT_PHOTO);
                }
            }
        });

        findViewById(R.id.changeAccount).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.checkPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    startActivityForResult(mCredential.newChooseAccountIntent(), Utils.REQUEST_ACCOUNT_PICKER);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SELECT_PHOTO);
                }
            }
        });

        sendFabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getResultsFromApi(view);
            }
        });

    }

    private void init() {
        // Initializing Internet Checker
        internetDetector = new InternetDetector(getApplicationContext());

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        // Initializing Progress Dialog
        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Sending...");

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        sendFabButton = (FloatingActionButton) findViewById(R.id.fab);
        edtToAddress = (EditText) findViewById(R.id.to_address);
        edtSubject = (EditText) findViewById(R.id.subject);
        edtMessage = (EditText) findViewById(R.id.body);
        edtAttachmentData = (EditText) findViewById(R.id.attachmentData);

    }

    private void showMessage(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }

    private void getResultsFromApi(View view) {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount(view);
        } else if (!internetDetector.checkMobileInternetConn()) {
            showMessage(view, "No network connection available.");
        } else if (!Utils.isNotEmpty(edtToAddress)) {
            showMessage(view, "To address Required");
        } else if (!Utils.isNotEmpty(edtSubject)) {
            showMessage(view, "Subject Required");
        } else if (!Utils.isNotEmpty(edtMessage)) {
            showMessage(view, "Message Required");
        } else {
            new MakeRequestTask(this, mCredential).execute();
        }
    }

    // Method for Checking Google Play Service is Available
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    // Method to Show Info, If Google Play Service is Not Available.
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    // Method for Google Play Services Error Info
    void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                Utils.REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    // Storing Mail ID using Shared Preferences
    private void chooseAccount(View view) {
        if (Utils.checkPermission(getApplicationContext(), Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi(view);
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(mCredential.newChooseAccountIntent(), Utils.REQUEST_ACCOUNT_PICKER);
            }
        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.GET_ACCOUNTS}, Utils.REQUEST_PERMISSION_GET_ACCOUNTS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case Utils.REQUEST_PERMISSION_GET_ACCOUNTS:
                chooseAccount(sendFabButton);
                break;
            case SELECT_PHOTO:
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, SELECT_PHOTO);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Utils.REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    showMessage(sendFabButton, "This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi(sendFabButton);
                }
                break;
            case Utils.REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi(sendFabButton);
                    }
                }
                break;
            case Utils.REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi(sendFabButton);
                }
                break;
            case SELECT_PHOTO:
                if (resultCode == RESULT_OK) {
                    final Uri imageUri = data.getData();
                    fileName = getPathFromURI(imageUri);
                    edtAttachmentData.setText(fileName);
                }
        }
    }

    public String getPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, "", null, "");
        assert cursor != null;
        if (cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }

    // Async Task for sending Mail using GMail OAuth
    private class MakeRequestTask extends AsyncTask<Void, Void, String> {

        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;
        private View view = sendFabButton;
        private MainActivity activity;

        MakeRequestTask(MainActivity activity, GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(getResources().getString(R.string.app_name))
                    .build();
            this.activity = activity;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        private String getDataFromApi() throws IOException {
            // getting Values for to Address, from Address, Subject and Body
            String user = "me";
            String to = Utils.getString(edtToAddress);
            String from = mCredential.getSelectedAccountName();
            String subject = Utils.getString(edtSubject);
            String body = Utils.getString(edtMessage);
            MimeMessage mimeMessage;
            String response = "";
            try {
                mimeMessage = createEmail(to, from, subject, body);
                response = sendMessage(mService, user, mimeMessage);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
            return response;
        }

        // Method to send email
        private String sendMessage(Gmail service,
                                   String userId,
                                   MimeMessage email)
                throws MessagingException, IOException {
            Message message = createMessageWithEmail(email);
            // GMail's official method to send email with oauth2.0
            message = service.users().messages().send(userId, message).execute();

            System.out.println("Message id: " + message.getId());
            System.out.println(message.toPrettyString());
            return message.getId();
        }

        // Method to create email Params
        private MimeMessage createEmail(String to,
                                        String from,
                                        String subject,
                                        String bodyText) throws MessagingException {
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);

            MimeMessage email = new MimeMessage(session);
            InternetAddress tAddress = new InternetAddress(to);
            InternetAddress fAddress = new InternetAddress(from);

            email.setFrom(fAddress);
            email.addRecipient(javax.mail.Message.RecipientType.TO, tAddress);
            email.setSubject(subject);

            // Create Multipart object and add MimeBodyPart objects to this object
            Multipart multipart = new MimeMultipart();

            // Changed for adding attachment and text
            // email.setText(bodyText);

            BodyPart textBody = new MimeBodyPart();
            textBody.setText(bodyText);
            multipart.addBodyPart(textBody);

            if (!(activity.fileName.equals(""))) {
                // Create new MimeBodyPart object and set DataHandler object to this object
                MimeBodyPart attachmentBody = new MimeBodyPart();
                String filename = activity.fileName; // change accordingly
                DataSource source = new FileDataSource(filename);
                attachmentBody.setDataHandler(new DataHandler(source));
                attachmentBody.setFileName(filename);
                multipart.addBodyPart(attachmentBody);
            }

            //Set the multipart object to the message object
            email.setContent(multipart);
            return email;
        }

        private Message createMessageWithEmail(MimeMessage email)
                throws MessagingException, IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            email.writeTo(bytes);
            String encodedEmail = Base64.encodeBase64URLSafeString(bytes.toByteArray());
            Message message = new Message();
            message.setRaw(encodedEmail);
            return message;
        }

        @Override
        protected void onPreExecute() {
            mProgress.show();
        }

        @Override
        protected void onPostExecute(String output) {
            mProgress.hide();
            if (output == null || output.length() == 0) {
                showMessage(view, "No results returned.");
            } else {
                showMessage(view, output);
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Utils.REQUEST_AUTHORIZATION);
                } else {
                    showMessage(view, "The following error occurred:\n" + mLastError);
                    Log.v("Error", mLastError + "");
                }
            } else {
                showMessage(view, "Request Cancelled.");
            }
        }
    }

}

