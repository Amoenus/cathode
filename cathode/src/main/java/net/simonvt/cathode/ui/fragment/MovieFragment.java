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
package net.simonvt.cathode.ui.fragment;

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.OnClick;
import com.squareup.otto.Bus;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.api.enumeration.ItemType;
import net.simonvt.cathode.api.util.TraktUtils;
import net.simonvt.cathode.database.SimpleCursor;
import net.simonvt.cathode.database.SimpleCursorLoader;
import net.simonvt.cathode.jobqueue.Job;
import net.simonvt.cathode.provider.DatabaseContract;
import net.simonvt.cathode.provider.DatabaseContract.CommentColumns;
import net.simonvt.cathode.provider.DatabaseContract.MovieCastColumns;
import net.simonvt.cathode.provider.DatabaseContract.MovieColumns;
import net.simonvt.cathode.provider.DatabaseContract.PersonColumns;
import net.simonvt.cathode.provider.DatabaseContract.UserColumns;
import net.simonvt.cathode.provider.DatabaseSchematic.Tables;
import net.simonvt.cathode.provider.ProviderSchematic;
import net.simonvt.cathode.provider.ProviderSchematic.Comments;
import net.simonvt.cathode.provider.ProviderSchematic.Movies;
import net.simonvt.cathode.scheduler.MovieTaskScheduler;
import net.simonvt.cathode.settings.TraktTimestamps;
import net.simonvt.cathode.ui.Loaders;
import net.simonvt.cathode.ui.NavigationListener;
import net.simonvt.cathode.ui.adapter.LinearCommentsAdapter;
import net.simonvt.cathode.ui.dialog.CheckInDialog;
import net.simonvt.cathode.ui.dialog.CheckInDialog.Type;
import net.simonvt.cathode.ui.dialog.ListsDialog;
import net.simonvt.cathode.ui.dialog.RatingDialog;
import net.simonvt.cathode.util.Intents;
import net.simonvt.cathode.util.SqlColumn;
import net.simonvt.cathode.widget.CircleTransformation;
import net.simonvt.cathode.widget.CircularProgressIndicator;
import net.simonvt.cathode.widget.RemoteImageView;
import net.simonvt.schematic.Cursors;

public class MovieFragment extends RefreshableAppBarFragment
    implements LoaderManager.LoaderCallbacks<SimpleCursor> {

  private static final String ARG_ID = "net.simonvt.cathode.ui.fragment.MovieFragment.id";
  private static final String ARG_TITLE = "net.simonvt.cathode.ui.fragment.MovieFragment.title";
  private static final String ARG_OVERVIEW =
      "net.simonvt.cathode.ui.fragment.MovieFragment.overview";

  private static final String DIALOG_RATING =
      "net.simonvt.cathode.ui.fragment.MovieFragment.ratingDialog";
  private static final String DIALOG_LISTS_ADD =
      "net.simonvt.cathode.ui.fragment.MovieFragment.listsAddDialog";
  private static final String DIALOG_COMMENT_ADD =
      "net.simonvt.cathode.ui.fragment.MovieFragment.addCommentDialog";
  private static final String DIALOG_COMMENT_UPDATE =
      "net.simonvt.cathode.ui.fragment.MovieFragment.updateCommentDialog";

  @Inject MovieTaskScheduler movieScheduler;
  @Inject Bus bus;

  @BindView(R.id.year) TextView year;
  @BindView(R.id.certification) TextView certification;
  //@BindView(R.id.poster) RemoteImageView poster;
  @BindView(R.id.overview) TextView overview;
  @BindView(R.id.isWatched) TextView isWatched;
  @BindView(R.id.inCollection) TextView collection;
  @BindView(R.id.inWatchlist) TextView watchlist;
  @BindView(R.id.rating) CircularProgressIndicator rating;

  @BindView(R.id.trailer) View trailer;

  @BindView(R.id.actorsParent) View actorsParent;
  @BindView(R.id.actorsHeader) View actorsHeader;
  @BindView(R.id.actors) LinearLayout actors;
  @BindView(R.id.peopleContainer) LinearLayout peopleContainer;

  @BindView(R.id.commentsParent) View commentsParent;
  @BindView(R.id.commentsHeader) View commentsHeader;
  @BindView(R.id.commentsContainer) LinearLayout commentsContainer;

  @BindView(R.id.websiteTitle) View websiteTitle;
  @BindView(R.id.website) TextView website;

  @BindView(R.id.viewOnTitle) View viewOnTitle;
  @BindView(R.id.viewOnContainer) ViewGroup viewOnContainer;
  @BindView(R.id.viewOnImdb) View viewOnImdb;
  @BindView(R.id.viewOnTmdb) View viewOnTmdb;

  private Cursor userComments;
  private Cursor comments;

  private long movieId;

  private String movieTitle;

  private String movieOverview;

  private int currentRating;

  private boolean loaded;

  private boolean watched;

  private boolean collected;

  private boolean inWatchlist;

  private boolean watching;

  private boolean checkedIn;

  private NavigationListener navigationListener;

  public static Bundle getArgs(long movieId, String movieTitle, String overview) {
    Bundle args = new Bundle();
    args.putLong(ARG_ID, movieId);
    args.putString(ARG_TITLE, movieTitle);
    args.putString(ARG_OVERVIEW, overview);
    return args;
  }

  @Override public void onAttach(Activity activity) {
    super.onAttach(activity);
    navigationListener = (NavigationListener) activity;
  }

  @Override public void onCreate(Bundle inState) {
    super.onCreate(inState);
    CathodeApp.inject(getActivity(), this);

    Bundle args = getArguments();
    movieId = args.getLong(ARG_ID);
    movieTitle = args.getString(ARG_TITLE);
    movieOverview = args.getString(ARG_OVERVIEW);

    setTitle(movieTitle);
  }

  @Override public View createView(LayoutInflater inflater, ViewGroup container, Bundle inState) {
    return inflater.inflate(R.layout.fragment_movie, container, false);
  }

  @Override public void onViewCreated(View view, Bundle inState) {
    super.onViewCreated(view, inState);
    overview.setText(movieOverview);

    getLoaderManager().initLoader(Loaders.MOVIE, null, this);
    getLoaderManager().initLoader(Loaders.MOVIE_ACTORS, null, actorsLoader);
    getLoaderManager().initLoader(Loaders.MOVIE_USER_COMMENTS, null, userCommentsLoader);
    getLoaderManager().initLoader(Loaders.MOVIE_COMMENTS, null, commentsLoader);
  }

  @OnClick(R.id.rating) void onRatingClick() {
    RatingDialog.newInstance(RatingDialog.Type.MOVIE, movieId, currentRating)
        .show(getFragmentManager(), DIALOG_RATING);
  }

  @OnClick(R.id.actorsHeader) void onShowActors() {
    navigationListener.onDisplayMovieActors(movieId, movieTitle);
  }

  @OnClick(R.id.commentsHeader) void onShowComments() {
    navigationListener.onDisplayComments(ItemType.MOVIE, movieId);
  }

  private Job.OnDoneListener onDoneListener = new Job.OnDoneListener() {
    @Override public void onDone(Job job) {
      setRefreshing(false);
    }
  };

  @Override public void onRefresh() {
    movieScheduler.sync(movieId, onDoneListener);
  }

  @Override public void createMenu(Toolbar toolbar) {
    super.createMenu(toolbar);
    Menu menu = toolbar.getMenu();

    toolbar.inflateMenu(R.menu.fragment_movie);

    if (loaded) {
      if (checkedIn) {
        menu.add(0, R.id.action_checkin_cancel, 1, R.string.action_checkin_cancel)
            .setIcon(R.drawable.ic_action_cancel)
            .setShowAsActionFlags(
                MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
      } else if (!watching) {
        menu.add(0, R.id.action_checkin, 2, R.string.action_checkin)
            .setIcon(R.drawable.ic_action_checkin)
            .setShowAsActionFlags(
                MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
      }

      if (watched) {
        menu.add(0, R.id.action_unwatched, 3, R.string.action_unwatched);
      } else {
        menu.add(0, R.id.action_watched, 4, R.string.action_watched);
        if (inWatchlist) {
          menu.add(0, R.id.action_watchlist_remove, 7, R.string.action_watchlist_remove);
        } else {
          menu.add(0, R.id.action_watchlist_add, 8, R.string.action_watchlist_add);
        }
      }

      if (collected) {
        menu.add(0, R.id.action_collection_remove, 5, R.string.action_collection_remove);
      } else {
        menu.add(0, R.id.action_collection_add, 6, R.string.action_collection_add);
      }
    }
  }

  @Override public boolean onMenuItemClick(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.action_watched:
        movieScheduler.setWatched(movieId, true);
        return true;

      case R.id.action_unwatched:
        movieScheduler.setWatched(movieId, false);
        return true;

      case R.id.action_checkin:
        CheckInDialog.showDialogIfNecessary(getActivity(), Type.MOVIE, movieTitle, movieId);
        return true;

      case R.id.action_checkin_cancel:
        movieScheduler.cancelCheckin();
        return true;

      case R.id.action_watchlist_add:
        movieScheduler.setIsInWatchlist(movieId, true);
        return true;

      case R.id.action_watchlist_remove:
        movieScheduler.setIsInWatchlist(movieId, false);
        return true;

      case R.id.action_collection_add:
        movieScheduler.setIsInCollection(movieId, true);
        return true;

      case R.id.action_collection_remove:
        movieScheduler.setIsInCollection(movieId, false);
        return true;

      case R.id.menu_lists_add:
        ListsDialog.newInstance(DatabaseContract.ItemType.MOVIE, movieId)
            .show(getFragmentManager(), DIALOG_LISTS_ADD);
        return true;
    }

    return super.onMenuItemClick(item);
  }

  private void updateView(final Cursor cursor) {
    if (cursor == null || !cursor.moveToFirst()) return;
    loaded = true;

    final String title = Cursors.getString(cursor, MovieColumns.TITLE);
    if (title != null && !title.equals(movieTitle)) {
      movieTitle = title;
      setTitle(movieTitle);
    }
    final int year = Cursors.getInt(cursor, MovieColumns.YEAR);
    final String certification = Cursors.getString(cursor, MovieColumns.CERTIFICATION);

    final String fanartUrl = Cursors.getString(cursor, MovieColumns.FANART);
    setBackdrop(fanartUrl, true);
    final String posterUrl = Cursors.getString(cursor, MovieColumns.POSTER);
    //poster.setImage(posterUrl);

    currentRating = Cursors.getInt(cursor, MovieColumns.USER_RATING);
    final float ratingAll = Cursors.getFloat(cursor, MovieColumns.RATING);
    rating.setValue(ratingAll);

    movieOverview = Cursors.getString(cursor, MovieColumns.OVERVIEW);
    watched = Cursors.getInt(cursor, MovieColumns.WATCHED) == 1;
    collected = Cursors.getInt(cursor, MovieColumns.IN_COLLECTION) == 1;
    inWatchlist = Cursors.getInt(cursor, MovieColumns.IN_WATCHLIST) == 1;
    watching = Cursors.getInt(cursor, MovieColumns.WATCHING) == 1;
    checkedIn = Cursors.getInt(cursor, MovieColumns.CHECKED_IN) == 1;

    isWatched.setVisibility(watched ? View.VISIBLE : View.GONE);
    collection.setVisibility(collected ? View.VISIBLE : View.GONE);
    watchlist.setVisibility(inWatchlist ? View.VISIBLE : View.GONE);

    this.year.setText(String.valueOf(year));
    this.certification.setText(certification);
    this.overview.setText(movieOverview);

    final String trailer = Cursors.getString(cursor, MovieColumns.TRAILER);
    if (!TextUtils.isEmpty(trailer)) {
      this.trailer.setVisibility(View.VISIBLE);
      this.trailer.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          Intents.openUrl(getActivity(), trailer);
        }
      });
    } else {
      this.trailer.setVisibility(View.GONE);
    }

    final long lastSync = Cursors.getLong(cursor, MovieColumns.LAST_SYNC);
    final long lastCommentSync = Cursors.getLong(cursor, MovieColumns.LAST_COMMENT_SYNC);
    if (TraktTimestamps.shouldSyncComments(lastCommentSync)) {
      movieScheduler.syncComments(movieId);
    }

    final long lastActorsSync = Cursors.getLong(cursor, MovieColumns.LAST_CREW_SYNC);
    if (lastSync > lastActorsSync) {
      movieScheduler.syncCrew(movieId);
    }

    final String website = Cursors.getString(cursor, DatabaseContract.ShowColumns.HOMEPAGE);
    if (!TextUtils.isEmpty(website)) {
      this.websiteTitle.setVisibility(View.VISIBLE);
      this.website.setVisibility(View.VISIBLE);

      this.website.setText(website);
      this.website.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          Intents.openUrl(getContext(), website);
        }
      });
    } else {
      this.websiteTitle.setVisibility(View.GONE);
      this.website.setVisibility(View.GONE);
    }

    final String imdbId = Cursors.getString(cursor, DatabaseContract.MovieColumns.IMDB_ID);
    final int tmdbId = Cursors.getInt(cursor, DatabaseContract.MovieColumns.TMDB_ID);

    final boolean hasImdbId = !TextUtils.isEmpty(imdbId);
    if (hasImdbId) {
      viewOnImdb.setVisibility(View.VISIBLE);
      viewOnImdb.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          Intents.openUrl(getContext(), TraktUtils.getImdbUrl(imdbId));
        }
      });
    } else {
      viewOnImdb.setVisibility(View.GONE);
    }

    if (tmdbId > 0) {
      viewOnTmdb.setVisibility(View.VISIBLE);
      viewOnTmdb.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) {
          Intents.openUrl(getContext(), TraktUtils.getTmdbTvUrl(tmdbId));
        }
      });
    } else {
      viewOnTmdb.setVisibility(View.GONE);
    }

    if (hasImdbId || tmdbId > 0) {
      viewOnTitle.setVisibility(View.VISIBLE);
      viewOnContainer.setVisibility(View.VISIBLE);
    } else {
      viewOnTitle.setVisibility(View.GONE);
      viewOnContainer.setVisibility(View.GONE);
    }

    invalidateMenu();
  }

  @Override public Loader<SimpleCursor> onCreateLoader(int i, Bundle bundle) {
    SimpleCursorLoader loader =
        new SimpleCursorLoader(getActivity(), Movies.withId(movieId), null, null, null, null);
    loader.setUpdateThrottle(2 * DateUtils.SECOND_IN_MILLIS);
    return loader;
  }

  @Override public void onLoadFinished(Loader<SimpleCursor> cursorLoader, SimpleCursor cursor) {
    updateView(cursor);
  }

  @Override public void onLoaderReset(Loader<SimpleCursor> cursorLoader) {
  }

  private void updateActors(Cursor c) {
    peopleContainer.removeAllViews();

    final int count = c.getCount();
    final int visibility = count > 0 ? View.VISIBLE : View.GONE;
    actorsParent.setVisibility(visibility);

    int index = 0;

    c.moveToPosition(-1);
    while (c.moveToNext() && index < 3) {
      View v = LayoutInflater.from(getActivity()).inflate(R.layout.item_person, actors, false);

      RemoteImageView headshot = (RemoteImageView) v.findViewById(R.id.headshot);
      headshot.addTransformation(new CircleTransformation());
      headshot.setImage(Cursors.getString(c, PersonColumns.HEADSHOT));

      TextView name = (TextView) v.findViewById(R.id.person_name);
      name.setText(Cursors.getString(c, PersonColumns.NAME));

      TextView character = (TextView) v.findViewById(R.id.person_job);
      character.setText(Cursors.getString(c, MovieCastColumns.CHARACTER));

      peopleContainer.addView(v);

      index++;
    }
  }

  private void updateComments() {
    LinearCommentsAdapter.updateComments(getContext(), commentsContainer, userComments, comments);
    commentsParent.setVisibility(View.VISIBLE);
  }

  private static final String[] CAST_PROJECTION = new String[] {
      Tables.MOVIE_CAST + "." + MovieCastColumns.ID,
      Tables.MOVIE_CAST + "." + MovieCastColumns.PERSON_ID,
      Tables.MOVIE_CAST + "." + MovieCastColumns.CHARACTER,
      Tables.PEOPLE + "." + PersonColumns.NAME, Tables.PEOPLE + "." + PersonColumns.HEADSHOT,
  };

  private LoaderManager.LoaderCallbacks<SimpleCursor> actorsLoader =
      new LoaderManager.LoaderCallbacks<SimpleCursor>() {
        @Override public Loader<SimpleCursor> onCreateLoader(int i, Bundle bundle) {
          SimpleCursorLoader loader =
              new SimpleCursorLoader(getActivity(), ProviderSchematic.MovieCast.fromMovie(movieId),
                  CAST_PROJECTION, Tables.PEOPLE + "." + PersonColumns.NEEDS_SYNC + "=0", null,
                  null);
          loader.setUpdateThrottle(2 * DateUtils.SECOND_IN_MILLIS);
          return loader;
        }

        @Override
        public void onLoadFinished(Loader<SimpleCursor> cursorLoader, SimpleCursor cursor) {
          updateActors(cursor);
        }

        @Override public void onLoaderReset(Loader<SimpleCursor> cursorLoader) {
        }
      };

  private static final String[] COMMENTS_PROJECTION = new String[] {
      SqlColumn.table(Tables.COMMENTS).column(CommentColumns.ID),
      SqlColumn.table(Tables.COMMENTS).column(CommentColumns.COMMENT),
      SqlColumn.table(Tables.COMMENTS).column(CommentColumns.SPOILER),
      SqlColumn.table(Tables.COMMENTS).column(CommentColumns.REVIEW),
      SqlColumn.table(Tables.COMMENTS).column(CommentColumns.CREATED_AT),
      SqlColumn.table(Tables.COMMENTS).column(CommentColumns.LIKES),
      SqlColumn.table(Tables.COMMENTS).column(CommentColumns.USER_RATING),
      SqlColumn.table(Tables.USERS).column(UserColumns.USERNAME),
      SqlColumn.table(Tables.USERS).column(UserColumns.NAME),
      SqlColumn.table(Tables.USERS).column(UserColumns.AVATAR),
  };

  private LoaderManager.LoaderCallbacks<SimpleCursor> userCommentsLoader =
      new LoaderManager.LoaderCallbacks<SimpleCursor>() {
        @Override public Loader<SimpleCursor> onCreateLoader(int id, Bundle args) {
          SimpleCursorLoader loader =
              new SimpleCursorLoader(getContext(), Comments.fromMovie(movieId), COMMENTS_PROJECTION,
                  CommentColumns.IS_USER_COMMENT + "=1", null, null);
          loader.setUpdateThrottle(2 * android.text.format.DateUtils.SECOND_IN_MILLIS);
          return loader;
        }

        @Override public void onLoadFinished(Loader<SimpleCursor> loader, SimpleCursor data) {
          userComments = data;
          updateComments();
        }

        @Override public void onLoaderReset(Loader<SimpleCursor> loader) {
        }
      };

  private LoaderManager.LoaderCallbacks<SimpleCursor> commentsLoader =
      new LoaderManager.LoaderCallbacks<SimpleCursor>() {
        @Override public Loader<SimpleCursor> onCreateLoader(int id, Bundle args) {
          SimpleCursorLoader loader =
              new SimpleCursorLoader(getContext(), Comments.fromMovie(movieId), COMMENTS_PROJECTION,
                  CommentColumns.IS_USER_COMMENT + "=0 AND " + CommentColumns.SPOILER + "=0", null,
                  CommentColumns.LIKES + " DESC LIMIT 3");
          loader.setUpdateThrottle(2 * android.text.format.DateUtils.SECOND_IN_MILLIS);
          return loader;
        }

        @Override public void onLoadFinished(Loader<SimpleCursor> loader, SimpleCursor data) {
          comments = data;
          updateComments();
        }

        @Override public void onLoaderReset(Loader<SimpleCursor> loader) {
        }
      };
}
