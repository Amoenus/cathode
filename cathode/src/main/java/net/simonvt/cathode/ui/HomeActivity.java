/*
 * Copyright (C) 2013 Simon Vig Therkildsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.simonvt.cathode.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.DrawerLayout;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.ProgressBar;
import butterknife.BindView;
import butterknife.ButterKnife;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.api.enumeration.ItemType;
import net.simonvt.cathode.database.SimpleCursor;
import net.simonvt.cathode.database.SimpleCursorLoader;
import net.simonvt.cathode.event.CheckInFailedEvent;
import net.simonvt.cathode.event.CheckInFailedEvent.OnCheckInFailedListener;
import net.simonvt.cathode.event.RequestFailedEvent;
import net.simonvt.cathode.event.RequestFailedEvent.OnRequestFailedListener;
import net.simonvt.cathode.event.SyncEvent;
import net.simonvt.cathode.event.SyncEvent.OnSyncListener;
import net.simonvt.cathode.provider.DatabaseContract.EpisodeColumns;
import net.simonvt.cathode.provider.DatabaseContract.MovieColumns;
import net.simonvt.cathode.provider.DatabaseContract.ShowColumns;
import net.simonvt.cathode.provider.DatabaseSchematic;
import net.simonvt.cathode.provider.ProviderSchematic;
import net.simonvt.cathode.search.SearchFragment;
import net.simonvt.cathode.settings.Settings;
import net.simonvt.cathode.ui.fragment.ActorsFragment;
import net.simonvt.cathode.ui.fragment.CommentFragment;
import net.simonvt.cathode.ui.fragment.CommentsFragment;
import net.simonvt.cathode.ui.fragment.DashboardFragment;
import net.simonvt.cathode.ui.fragment.EpisodeFragment;
import net.simonvt.cathode.ui.lists.ListFragment;
import net.simonvt.cathode.ui.lists.ListsFragment;
import net.simonvt.cathode.ui.fragment.MovieCollectionFragment;
import net.simonvt.cathode.ui.fragment.MovieFragment;
import net.simonvt.cathode.ui.fragment.MovieSuggestionsFragment;
import net.simonvt.cathode.ui.fragment.MovieWatchlistFragment;
import net.simonvt.cathode.ui.fragment.NavigationFragment;
import net.simonvt.cathode.ui.fragment.SeasonFragment;
import net.simonvt.cathode.ui.fragment.ShowFragment;
import net.simonvt.cathode.ui.fragment.ShowSuggestionsFragment;
import net.simonvt.cathode.ui.fragment.ShowsCollectionFragment;
import net.simonvt.cathode.ui.fragment.ShowsWatchlistFragment;
import net.simonvt.cathode.ui.fragment.StatsFragment;
import net.simonvt.cathode.ui.fragment.UpcomingShowsFragment;
import net.simonvt.cathode.ui.fragment.WatchedMoviesFragment;
import net.simonvt.cathode.ui.fragment.WatchedShowsFragment;
import net.simonvt.cathode.ui.login.LoginActivity;
import net.simonvt.cathode.util.FragmentStack;
import net.simonvt.cathode.util.MainHandler;
import net.simonvt.cathode.widget.Crouton;
import net.simonvt.cathode.widget.WatchingView;
import net.simonvt.cathode.widget.WatchingView.WatchingViewListener;
import net.simonvt.schematic.Cursors;
import timber.log.Timber;

public class HomeActivity extends BaseActivity
    implements NavigationFragment.OnMenuClickListener, NavigationListener {

  private class PendingReplacement {

    Class fragment;

    String tag;

    public PendingReplacement(Class fragment, String tag) {
      this.fragment = fragment;
      this.tag = tag;
    }
  }

  public static final String DIALOG_ABOUT = "net.simonvt.cathode.ui.BaseActivity.aboutDialog";

  private static final String STATE_STACK = "net.simonvt.cathode.ui.HomeActivity.stack";

  public static final String ACTION_LOGIN = "net.simonvt.cathode.intent.action.LOGIN";
  public static final String DIALOG_LOGOUT = "net.simonvt.cathode.ui.HomeActivity.logoutDialog";

  private static final int LOADER_SHOW_WATCHING = 1;
  private static final int LOADER_MOVIE_WATCHING = 2;

  @BindView(R.id.progress_top) ProgressBar progressTop;

  @BindView(R.id.crouton) Crouton crouton;

  private FragmentStack stack;

  @BindView(R.id.drawer) DrawerLayout drawer;
  private int drawerState = DrawerLayout.STATE_IDLE;
  private NavigationFragment navigation;

  @BindView(R.id.watching_parent) ViewGroup watchingParent;
  @BindView(R.id.watchingView) WatchingView watchingView;

  private Cursor watchingShow;
  private Cursor watchingMovie;

  private boolean isTablet;

  private PendingReplacement pendingReplacement;

  private boolean isSyncing = false;

  @Override protected void onCreate(Bundle inState) {
    super.onCreate(inState);
    Timber.d("onCreate");
    CathodeApp.inject(this);

    setContentView(R.layout.activity_home);

    ButterKnife.bind(this);

    isTablet = getResources().getBoolean(R.bool.isTablet);

    navigation =
        (NavigationFragment) getSupportFragmentManager().findFragmentByTag(NavigationFragment.TAG);

    watchingParent.setOnTouchListener(new View.OnTouchListener() {
      @SuppressLint("ClickableViewAccessibility") @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (watchingView.isExpanded()) {
          final int action = event.getActionMasked();
          if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            watchingView.collapse();
          }

          return true;
        }

        return false;
      }
    });
    watchingView.setWatchingViewListener(watchingListener);

    stack = FragmentStack.forContainer(this, R.id.content);
    stack.setDefaultAnimation(R.anim.fade_in_front, R.anim.fade_out_back, R.anim.fade_in_back,
        R.anim.fade_out_front);
    if (inState != null) {
      stack.restoreState(inState.getBundle(STATE_STACK));
    }
    if (stack.size() == 0) {
      SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
      final String startPagePref = settings.getString(Settings.START_PAGE, null);
      StartPage startPage = StartPage.fromValue(startPagePref, StartPage.DASHBOARD);
      stack.replace(startPage.getPageClass(), startPage.getTag());
    }

    getSupportLoaderManager().initLoader(LOADER_SHOW_WATCHING, null, watchingShowCallback);
    getSupportLoaderManager().initLoader(LOADER_MOVIE_WATCHING, null, watchingMovieCallback);

    drawer.addDrawerListener(new DrawerLayout.DrawerListener() {
      @Override public void onDrawerSlide(View drawerView, float slideOffset) {
      }

      @Override public void onDrawerOpened(View drawerView) {
        pendingReplacement = null;
      }

      @Override public void onDrawerClosed(View drawerView) {
        if (pendingReplacement != null) {
          stack.replace(pendingReplacement.fragment, pendingReplacement.tag);
          pendingReplacement = null;
        }
      }

      @Override public void onDrawerStateChanged(int newState) {
        drawerState = newState;
        if (newState == DrawerLayout.STATE_DRAGGING) {
          pendingReplacement = null;
        }
      }
    });

    if (!Settings.isLoggedIn(this) || isLoginAction(getIntent())) {
      startLoginActivity();
    }

    SyncEvent.registerListener(onSyncEvent);
    RequestFailedEvent.registerListener(requestFailedListener);
    CheckInFailedEvent.registerListener(checkInFailedListener);
  }

  @Override protected void onNewIntent(Intent intent) {
    if (isLoginAction(intent)) {
      MainHandler.post(new Runnable() {
        @Override public void run() {
          startLoginActivity();
        }
      });
    }
  }

  private boolean isLoginAction(Intent intent) {
    return ACTION_LOGIN.equals(intent.getAction());
  }

  @Override protected void onSaveInstanceState(Bundle outState) {
    outState.putBundle(STATE_STACK, stack.saveState());
    super.onSaveInstanceState(outState);
  }

  @Override protected void onResumeFragments() {
    super.onResumeFragments();
    stack.resume();
  }

  @Override protected void onPause() {
    stack.pause();
    super.onPause();
  }

  @Override protected void onDestroy() {
    Timber.d("onDestroy");
    SyncEvent.unregisterListener(onSyncEvent);
    RequestFailedEvent.unregisterListener(requestFailedListener);
    CheckInFailedEvent.unregisterListener(checkInFailedListener);
    super.onDestroy();
  }

  @Override public void onBackPressed() {
    if (watchingView.isExpanded()) {
      watchingView.collapse();
      return;
    }

    if (drawer.isDrawerVisible(Gravity.LEFT)) {
      drawer.closeDrawer(Gravity.LEFT);
      return;
    }

    final FragmentContract topFragment = (FragmentContract) stack.peek();
    if (topFragment != null && topFragment.onBackPressed()) {
      return;
    }

    if (stack.pop()) {
      return;
    }

    super.onBackPressed();
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        onHomeClicked();
        return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override public boolean onMenuItemClicked(int id) {
    switch (id) {
      case R.id.menu_dashboard:
        pendingReplacement = new PendingReplacement(DashboardFragment.class, DashboardFragment.TAG);
        break;

      case R.id.menu_shows_upcoming:
        pendingReplacement =
            new PendingReplacement(UpcomingShowsFragment.class, UpcomingShowsFragment.TAG);
        break;

      case R.id.menu_shows_watched:
        pendingReplacement =
            new PendingReplacement(WatchedShowsFragment.class, WatchedShowsFragment.TAG);
        break;

      case R.id.menu_shows_collection:
        pendingReplacement =
            new PendingReplacement(ShowsCollectionFragment.class, ShowsCollectionFragment.TAG);
        break;

      case R.id.menu_shows_watchlist:
        pendingReplacement =
            new PendingReplacement(ShowsWatchlistFragment.class, ShowsWatchlistFragment.TAG);
        break;

      case R.id.menu_shows_suggestions:
        pendingReplacement =
            new PendingReplacement(ShowSuggestionsFragment.class, ShowSuggestionsFragment.TAG);
        break;

      case R.id.menu_movies_watched:
        pendingReplacement =
            new PendingReplacement(WatchedMoviesFragment.class, WatchedMoviesFragment.TAG);
        break;

      case R.id.menu_movies_collection:
        pendingReplacement =
            new PendingReplacement(MovieCollectionFragment.class, MovieCollectionFragment.TAG);
        break;

      case R.id.menu_movies_watchlist:
        pendingReplacement =
            new PendingReplacement(MovieWatchlistFragment.class, MovieWatchlistFragment.TAG);
        break;

      case R.id.menu_movies_suggestions:
        pendingReplacement =
            new PendingReplacement(MovieSuggestionsFragment.class, MovieSuggestionsFragment.TAG);
        break;

      case R.id.menu_lists:
        pendingReplacement = new PendingReplacement(ListsFragment.class, ListsFragment.TAG);
        break;

      case R.id.menu_stats:
        pendingReplacement = new PendingReplacement(StatsFragment.class, StatsFragment.TAG);
        break;

      case R.id.menu_settings:
        Intent settings = new Intent(this, SettingsActivity.class);
        startActivity(settings);
        return false;

      default:
        throw new IllegalArgumentException("Unknown id " + id);
    }

    drawer.closeDrawer(Gravity.LEFT);
    return true;
  }

  private WatchingViewListener watchingListener = new WatchingViewListener() {
    @Override public void onExpand(WatchingView view) {
      Timber.d("onExpand");
    }

    @Override public void onCollapse(WatchingView view) {
      Timber.d("onCollapse");
    }

    @Override public void onEpisodeClicked(WatchingView view, long episodeId, String showTitle) {
      watchingView.collapse();

      Fragment top = stack.peek();
      if (top instanceof EpisodeFragment) {
        EpisodeFragment f = (EpisodeFragment) top;
        if (episodeId == f.getEpisodeId()) {
          return;
        }
      }

      onDisplayEpisode(episodeId, showTitle);
    }

    @Override
    public void onMovieClicked(WatchingView view, long id, String title, String overview) {
      watchingView.collapse();

      Fragment top = stack.peek();
      if (top instanceof MovieFragment) {
        MovieFragment f = (MovieFragment) top;
        if (id == f.getMovieId()) {
          return;
        }
      }

      onDisplayMovie(id, title, overview);
    }

    @Override public void onAnimatingIn(WatchingView view) {
    }

    @Override public void onAnimatingOut(WatchingView view) {
    }
  };

  private OnSyncListener onSyncEvent = new OnSyncListener() {
    @Override public void onSyncChanged(int authSyncCount, int jobSyncCount) {
      final boolean isSyncing = authSyncCount > 0 || jobSyncCount > 0;
      if (isSyncing != HomeActivity.this.isSyncing) {
        HomeActivity.this.isSyncing = isSyncing;

        final int progressVisibility = progressTop.getVisibility();
        ViewPropertyAnimator progressAnimator = progressTop.animate();
        if (isSyncing) {
          if (progressVisibility == View.GONE) {
            progressTop.setAlpha(0.0f);
            progressTop.setVisibility(View.VISIBLE);
          }

          progressAnimator.alpha(1.0f);
        } else {
          progressAnimator.alpha(0.0f).withEndAction(new Runnable() {
            @Override public void run() {
              progressTop.setVisibility(View.GONE);
            }
          });
        }
      }
    }
  };

  private OnRequestFailedListener requestFailedListener = new OnRequestFailedListener() {
    @Override public void onRequestFailed(RequestFailedEvent event) {
      crouton.show(getString(event.getErrorMessage()),
          getResources().getColor(android.R.color.holo_red_dark));
    }
  };

  private OnCheckInFailedListener checkInFailedListener = new OnCheckInFailedListener() {
    @Override public void onCheckInFailed(CheckInFailedEvent event) {
      crouton.show(getResources().getString(R.string.checkin_error, event.getTitle()),
          getResources().getColor(android.R.color.holo_red_dark));
    }
  };

  private void startLoginActivity() {
    Intent login = new Intent(this, LoginActivity.class);
    startActivity(login);
    finish();
  }

  ///////////////////////////////////////////////////////////////////////////
  // Navigation callbacks
  ///////////////////////////////////////////////////////////////////////////

  @Override public void onHomeClicked() {
    final boolean drawerVisible = drawer.isDrawerVisible(Gravity.LEFT);
    if (stack.size() == 1) {
      drawer.openDrawer(Gravity.LEFT);
      return;
    }

    stack.pop();
  }

  @Override public void onSearchClicked() {
    stack.push(SearchFragment.class, SearchFragment.TAG);
  }

  @Override
  public void onDisplayShow(long showId, String title, String overview, LibraryType type) {
    stack.push(ShowFragment.class, ShowFragment.TAG,
        ShowFragment.getArgs(showId, title, overview, type));
  }

  @Override public void onDisplayEpisode(long episodeId, String showTitle) {
    stack.push(EpisodeFragment.class, EpisodeFragment.getTag(episodeId),
        EpisodeFragment.getArgs(episodeId, showTitle));
  }

  @Override
  public void onDisplaySeason(long showId, long seasonId, String showTitle, int seasonNumber,
      LibraryType type) {
    stack.push(SeasonFragment.class, SeasonFragment.TAG,
        SeasonFragment.getArgs(showId, seasonId, showTitle, seasonNumber, type));
  }

  @Override public void onDisplayShowActors(long showId, String title) {
    stack.push(ActorsFragment.class, ActorsFragment.TAG, ActorsFragment.forShow(showId, title));
  }

  @Override public void onDisplayMovie(long movieId, String title, String overview) {
    stack.push(MovieFragment.class, MovieFragment.getTag(movieId),
        MovieFragment.getArgs(movieId, title, overview));
  }

  @Override public void onDisplayMovieActors(long movieId, String title) {
    stack.push(ActorsFragment.class, ActorsFragment.TAG, ActorsFragment.forMovie(movieId, title));
  }

  @Override public void onShowList(long listId, String listName) {
    stack.push(ListFragment.class, ListFragment.TAG, ListFragment.getArgs(listId, listName));
  }

  @Override public void onListDeleted(long listId) {
    Fragment top = stack.peek();
    if (top instanceof ListFragment) {
      ListFragment f = (ListFragment) top;
      if (listId == f.getListId()) {
        stack.pop();
      }
    }
  }

  @Override public void onDisplayComments(ItemType type, long itemId) {
    stack.push(CommentsFragment.class, CommentsFragment.TAG,
        CommentsFragment.getArgs(type, itemId));
  }

  @Override public void onDisplayComment(long commentId) {
    stack.push(CommentFragment.class, CommentFragment.TAG, CommentFragment.getArgs(commentId));
  }

  @Override public void displayFragment(Class clazz, String tag) {
    stack.push(clazz, tag, null);
  }

  @Override public boolean isFragmentTopLevel(Fragment fragment) {
    return stack.positionInstack(fragment) == 0;
  }

  ///////////////////////////////////////////////////////////////////////////
  // Watching view
  ///////////////////////////////////////////////////////////////////////////

  private void updateWatching() {
    if (watchingShow != null && watchingShow.moveToFirst()) {
      final long showId = Cursors.getLong(watchingShow, ShowColumns.ID);
      final String show = Cursors.getString(watchingShow, ShowColumns.TITLE);
      final String poster = Cursors.getString(watchingShow, ShowColumns.POSTER);
      final String episode = Cursors.getString(watchingShow, EpisodeColumns.TITLE);
      final int season = Cursors.getInt(watchingShow, EpisodeColumns.SEASON);

      final long episodeId = Cursors.getLong(watchingShow, "episodeId");
      final String episodeTitle = Cursors.getString(watchingShow, EpisodeColumns.TITLE);
      final int episodeNumber = Cursors.getInt(watchingShow, EpisodeColumns.EPISODE);
      final boolean checkedIn = Cursors.getBoolean(watchingShow, EpisodeColumns.CHECKED_IN);
      final long startTime = Cursors.getLong(watchingShow, EpisodeColumns.STARTED_AT);
      final long endTime = Cursors.getLong(watchingShow, EpisodeColumns.EXPIRES_AT);

      watchingView.watchingShow(showId, show, episodeId, episodeTitle, poster, startTime, endTime);
    } else if (watchingMovie != null && watchingMovie.moveToFirst()) {
      final long id = Cursors.getLong(watchingMovie, MovieColumns.ID);
      final String title = Cursors.getString(watchingMovie, MovieColumns.TITLE);
      final String overview = Cursors.getString(watchingMovie, MovieColumns.OVERVIEW);
      final String poster = Cursors.getString(watchingMovie, MovieColumns.POSTER);
      final long startTime = Cursors.getLong(watchingMovie, MovieColumns.STARTED_AT);
      final long endTime = Cursors.getLong(watchingMovie, MovieColumns.EXPIRES_AT);

      watchingView.watchingMovie(id, title, overview, poster, startTime, endTime);
    } else {
      watchingView.clearWatching();
    }
  }

  private static final String[] SHOW_WATCHING_PROJECTION = new String[] {
      DatabaseSchematic.Tables.SHOWS + "." + ShowColumns.ID,
      DatabaseSchematic.Tables.SHOWS + "." + ShowColumns.TITLE,
      DatabaseSchematic.Tables.SHOWS + "." + ShowColumns.POSTER,
      DatabaseSchematic.Tables.EPISODES + "." + EpisodeColumns.ID + " AS episodeId",
      DatabaseSchematic.Tables.EPISODES + "." + EpisodeColumns.TITLE,
      DatabaseSchematic.Tables.EPISODES + "." + EpisodeColumns.SEASON,
      DatabaseSchematic.Tables.EPISODES + "." + EpisodeColumns.EPISODE,
      DatabaseSchematic.Tables.EPISODES + "." + EpisodeColumns.CHECKED_IN,
      DatabaseSchematic.Tables.EPISODES + "." + EpisodeColumns.STARTED_AT,
      DatabaseSchematic.Tables.EPISODES + "." + EpisodeColumns.EXPIRES_AT,
  };

  private LoaderManager.LoaderCallbacks<SimpleCursor> watchingShowCallback =
      new LoaderManager.LoaderCallbacks<SimpleCursor>() {
        @Override public Loader<SimpleCursor> onCreateLoader(int i, Bundle bundle) {
          return new SimpleCursorLoader(HomeActivity.this, ProviderSchematic.Shows.SHOW_WATCHING,
              SHOW_WATCHING_PROJECTION, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<SimpleCursor> cursorLoader, SimpleCursor cursor) {
          watchingShow = cursor;
          updateWatching();
        }

        @Override public void onLoaderReset(Loader<SimpleCursor> cursorLoader) {
          watchingShow = null;
        }
      };

  private LoaderManager.LoaderCallbacks<SimpleCursor> watchingMovieCallback =
      new LoaderManager.LoaderCallbacks<SimpleCursor>() {
        @Override public Loader<SimpleCursor> onCreateLoader(int i, Bundle bundle) {
          return new SimpleCursorLoader(HomeActivity.this, ProviderSchematic.Movies.WATCHING, null,
              null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<SimpleCursor> cursorLoader, SimpleCursor cursor) {
          watchingMovie = cursor;
          updateWatching();
        }

        @Override public void onLoaderReset(Loader<SimpleCursor> cursorLoader) {
          watchingMovie = null;
        }
      };
}
