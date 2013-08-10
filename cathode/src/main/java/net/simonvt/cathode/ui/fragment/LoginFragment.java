package net.simonvt.cathode.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import butterknife.InjectView;
import com.squareup.otto.Bus;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.api.ResponseParser;
import net.simonvt.cathode.api.UserCredentials;
import net.simonvt.cathode.api.body.CreateAccountBody;
import net.simonvt.cathode.api.entity.TraktResponse;
import net.simonvt.cathode.api.service.AccountService;
import net.simonvt.cathode.event.LoginEvent;
import net.simonvt.cathode.event.MessageEvent;
import net.simonvt.cathode.remote.TraktTaskQueue;
import net.simonvt.cathode.remote.sync.SyncTask;
import net.simonvt.cathode.settings.Settings;
import net.simonvt.cathode.util.ApiUtils;
import net.simonvt.cathode.util.LogWrapper;
import retrofit.RetrofitError;

public class LoginFragment extends BaseFragment {

  private static final String TAG = "LoginFragment";

  @Inject AccountService accountService;
  @Inject UserCredentials credentials;
  @Inject TraktTaskQueue queue;
  @Inject Bus bus;

  @InjectView(R.id.username) EditText usernameInput;
  @InjectView(R.id.password) EditText passwordInput;
  @InjectView(R.id.email) EditText emailInput;
  @InjectView(R.id.createNew) CheckBox createNew;
  @InjectView(R.id.login) Button login;

  private String username;
  private String password;
  private String email;

  private Context appContext;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    CathodeApp.inject(getActivity(), this);
    appContext = getActivity().getApplicationContext();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_login, container, false);
  }

  @Override
  public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    login.setOnClickListener(onLoginListener);
    usernameInput.addTextChangedListener(textChanged);
    passwordInput.addTextChangedListener(textChanged);
    createNew.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        emailInput.setEnabled(((CheckBox) view).isChecked());
      }
    });

    login.setEnabled(usernameInput.length() > 0 && passwordInput.length() > 0);
  }

  @Override
  public String getTitle() {
    return getResources().getString(R.string.login);
  }

  private View.OnClickListener onLoginListener = new View.OnClickListener() {
    @Override
    public void onClick(View view) {
      username = usernameInput.getText().toString();
      password = passwordInput.getText().toString();
      email = emailInput.getText().toString();
      final boolean createNewUser = createNew.isChecked();

      login.setEnabled(false);
      if (createNewUser) {
        new CreateAccountAsync().execute();
      } else {
        new LoginAsync(username, password).execute();
      }
    }
  };

  private TextWatcher textChanged = new TextWatcher() {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
      login.setEnabled(usernameInput.length() > 0 && passwordInput.length() > 0);
    }
  };

  private final class CreateAccountAsync extends AsyncTask<Void, Void, Boolean> {

    @Override
    protected Boolean doInBackground(Void... voids) {
      TraktResponse r = accountService.create(new CreateAccountBody(username, password, email));
      LogWrapper.d(TAG, "Error: "
          + r.getError()
          + " - Status: "
          + r.getStatus()
          + " - Message: "
          + r.getMessage());

      return r.getError() == null;
    }

    @Override
    protected void onPostExecute(Boolean success) {
      if (success) {
        bus.post(new MessageEvent(R.string.login_success));

        final String username = LoginFragment.this.username;
        final String password = ApiUtils.getSha(LoginFragment.this.password);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(appContext);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(Settings.USERNAME, username);
        editor.putString(Settings.PASSWORD, password);
        editor.apply();

        credentials.setCredentials(username, password);
        queue.add(new SyncTask());

        bus.post(new LoginEvent(LoginFragment.this.username, LoginFragment.this.password));

        CathodeApp.setupAccount(appContext);
      } else {
        login.setEnabled(true);
        bus.post(new MessageEvent(R.string.create_user_failed));
      }
    }
  }

  private final class LoginAsync extends AsyncTask<Void, Void, Boolean> {

    private LoginAsync(String username, String password) {
      credentials.setCredentials(username, ApiUtils.getSha(password));
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
      try {
        TraktResponse r = accountService.test();
        LogWrapper.d(TAG, "Error: "
            + r.getError()
            + " - Status: "
            + r.getStatus()
            + " - Message: "
            + r.getMessage());

        return r.getError() == null;
      } catch (RetrofitError e) {
        ResponseParser parser = new ResponseParser();
        CathodeApp.inject(appContext, parser);
        TraktResponse r = parser.tryParse(e);
        if (r != null) {
          LogWrapper.d(TAG, "Error: "
              + r.getError()
              + " - Status: "
              + r.getStatus()
              + " - Message: "
              + r.getMessage());
        }
      }

      return false;
    }

    @Override
    protected void onPostExecute(Boolean success) {
      if (success) {
        bus.post(new MessageEvent(R.string.login_success));

        final String username = LoginFragment.this.username;
        final String password = ApiUtils.getSha(LoginFragment.this.password);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(appContext);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(Settings.USERNAME, username);
        editor.putString(Settings.PASSWORD, password);
        editor.apply();

        credentials.setCredentials(username, password);
        queue.add(new SyncTask());

        bus.post(new LoginEvent(LoginFragment.this.username, LoginFragment.this.password));

        CathodeApp.setupAccount(appContext);
      } else {
        login.setEnabled(true);
        credentials.setCredentials(null, null);
        bus.post(new MessageEvent(R.string.wrong_password));
      }
    }
  }
}