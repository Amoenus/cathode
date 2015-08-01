/*
 * Copyright (C) 2015 Simon Vig Therkildsen
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
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.View;
import javax.inject.Inject;
import net.simonvt.cathode.CathodeApp;
import net.simonvt.cathode.R;
import net.simonvt.cathode.database.SimpleCursor;
import net.simonvt.cathode.database.SimpleCursorLoader;
import net.simonvt.cathode.provider.DatabaseContract.EpisodeColumns;
import net.simonvt.cathode.provider.DatabaseContract.LastModifiedColumns;
import net.simonvt.cathode.provider.DatabaseContract.ListItemColumns;
import net.simonvt.cathode.provider.DatabaseContract.MovieColumns;
import net.simonvt.cathode.provider.DatabaseContract.PersonColumns;
import net.simonvt.cathode.provider.DatabaseContract.SeasonColumns;
import net.simonvt.cathode.provider.DatabaseContract.ShowColumns;
import net.simonvt.cathode.provider.DatabaseSchematic.Tables;
import net.simonvt.cathode.provider.ProviderSchematic;
import net.simonvt.cathode.scheduler.ListsTaskScheduler;
import net.simonvt.cathode.ui.LibraryType;
import net.simonvt.cathode.ui.Loaders;
import net.simonvt.cathode.ui.NavigationListener;
import net.simonvt.cathode.ui.adapter.ListAdapter;
import net.simonvt.cathode.ui.adapter.ShowClickListener;
import net.simonvt.cathode.ui.listener.EpisodeClickListener;
import net.simonvt.cathode.ui.listener.MovieClickListener;
import net.simonvt.cathode.ui.listener.SeasonClickListener;
import net.simonvt.cathode.util.SqlCoalesce;
import net.simonvt.cathode.util.SqlColumn;

public class ListFragment extends ToolbarGridFragment<RecyclerView.ViewHolder>
    implements LoaderManager.LoaderCallbacks<SimpleCursor>, ShowClickListener,
    SeasonClickListener, EpisodeClickListener, MovieClickListener,
    ListAdapter.OnRemoveItemListener {

  private static final String ARG_LIST_ID = "net.simonvt.cathode.ui.fragment.ListFragment.lidtId";
  private static final String ARG_LIST_NAME =
      "net.simonvt.cathode.ui.fragment.ListFragment.listName";

  @Inject ListsTaskScheduler listScheduler;

  private NavigationListener navigationListener;

  private long listId;

  private ListAdapter adapter;

  private int columnCount;

  public static Bundle getArgs(long listId, String listName) {
    Bundle args = new Bundle();
    args.putLong(ARG_LIST_ID, listId);
    args.putString(ARG_LIST_NAME, listName);
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
    listId = args.getLong(ARG_LIST_ID);
    String listName = args.getString(ARG_LIST_NAME);
    setTitle(listName);

    columnCount = getResources().getInteger(R.integer.listColumns);

    getLoaderManager().initLoader(Loaders.LIST, null, this);
  }

  @Override protected int getColumnCount() {
    return columnCount;
  }

  @Override public void onShowClick(View view, int position, long id) {
    Cursor cursor = adapter.getCursor(position);
    final String title = cursor.getString(cursor.getColumnIndex(ShowColumns.TITLE));
    navigationListener.onDisplayShow(id, title, LibraryType.WATCHED);
  }

  @Override public void onSeasonClick(View view, int position, long id) {
    Cursor cursor = adapter.getCursor(position);
    final long showId = cursor.getLong(cursor.getColumnIndex(SeasonColumns.SHOW_ID));
    final String showTitle = cursor.getString(cursor.getColumnIndex("seasonShowTitle"));
    final int seasonNumber = cursor.getInt(cursor.getColumnIndex(SeasonColumns.SEASON));
    navigationListener.onDisplaySeason(showId, id, showTitle, seasonNumber, LibraryType.WATCHED);
  }

  @Override public void onEpisodeClick(View view, int position, long id) {
    navigationListener.onDisplayEpisode(id, null);
  }

  @Override public void onMovieClicked(View v, int position, long id) {
    Cursor cursor = adapter.getCursor(position);
    final String title = cursor.getString(cursor.getColumnIndex(MovieColumns.TITLE));
    navigationListener.onDisplayMovie(id, title);
  }

  @Override public void onRemoveItem(int position, long id) {
    Loader loader = getLoaderManager().getLoader(Loaders.LOADER_LIST);
    ((SimpleCursorLoader) loader).throttle(2 * DateUtils.SECOND_IN_MILLIS);

    final SimpleCursor cursor = (SimpleCursor) adapter.getCursor(position);

    final long itemId = cursor.getLong(cursor.getColumnIndex(ListItemColumns.ITEM_ID));
    final int itemType = cursor.getInt(cursor.getColumnIndex(ListItemColumns.ITEM_TYPE));
    listScheduler.removeItem(listId, itemType, itemId);

    cursor.remove(id);
    adapter.notifyChanged();
  }

  private void setCursor(SimpleCursor cursor) {
    if (adapter == null) {
      adapter = new ListAdapter(getActivity(), this, this, this, this, this);
      setAdapter(adapter);
    }

    adapter.changeCursor(cursor);
  }

  private static final String[] PROJECTION = {
      SqlColumn.table(Tables.LIST_ITEMS).column(ListItemColumns.ID), ListItemColumns.ITEM_TYPE,
      ListItemColumns.ITEM_ID,

      SqlCoalesce.coaloesce(SqlColumn.table(Tables.SHOWS).column(ShowColumns.OVERVIEW),
          SqlColumn.table(Tables.MOVIES).column(MovieColumns.OVERVIEW))
          .as(ListItemColumns.OVERVIEW),

      SqlCoalesce.coaloesce(SqlColumn.table(Tables.SHOWS).column(ShowColumns.POSTER),
          SqlColumn.table(Tables.MOVIES).column(MovieColumns.POSTER)).as(ListItemColumns.POSTER),

      SqlColumn.table(Tables.SHOWS).column(ShowColumns.TITLE),
      SqlColumn.table(Tables.SHOWS).column(ShowColumns.WATCHED_COUNT),
      SqlColumn.table(Tables.SHOWS).column(ShowColumns.IN_COLLECTION_COUNT),
      SqlColumn.table(Tables.SHOWS).column(ShowColumns.IN_WATCHLIST),
      SqlColumn.table(Tables.SHOWS).column(ShowColumns.RATING),
      SqlColumn.table(Tables.SHOWS).column(LastModifiedColumns.LAST_MODIFIED),

      SqlColumn.table(Tables.SEASONS).column(SeasonColumns.SEASON),
      SqlColumn.table(Tables.SEASONS).column(SeasonColumns.SHOW_ID),
      SqlColumn.table(Tables.SEASONS).column(LastModifiedColumns.LAST_MODIFIED),
      "(SELECT " + ShowColumns.TITLE
      + " FROM " + Tables.SHOWS
      + " WHERE "
      + Tables.SHOWS + "." + ShowColumns.ID
      + "="
      + Tables.SEASONS + "." + SeasonColumns.SHOW_ID
      + ") AS seasonShowTitle",
      "(SELECT " + ShowColumns.POSTER
      + " FROM " + Tables.SHOWS
      + " WHERE "
      + Tables.SHOWS + "." + ShowColumns.ID
      + "="
      + Tables.SEASONS + "." + SeasonColumns.SHOW_ID
      + ") AS seasonShowPoster",

      SqlColumn.table(Tables.EPISODES).column(EpisodeColumns.TITLE),
      SqlColumn.table(Tables.EPISODES).column(EpisodeColumns.SEASON),
      SqlColumn.table(Tables.EPISODES).column(EpisodeColumns.EPISODE),
      SqlColumn.table(Tables.EPISODES).column(EpisodeColumns.WATCHED),
      SqlColumn.table(Tables.EPISODES).column(EpisodeColumns.FIRST_AIRED),
      SqlColumn.table(Tables.EPISODES).column(EpisodeColumns.SCREENSHOT),
      SqlColumn.table(Tables.EPISODES).column(LastModifiedColumns.LAST_MODIFIED),
      "(SELECT " + ShowColumns.TITLE
      + " FROM " + Tables.SHOWS
      + " WHERE "
      + Tables.SHOWS + "." + ShowColumns.ID
      + "="
      + Tables.EPISODES + "." + EpisodeColumns.SHOW_ID
      + ") AS episodeShowTitle",

      SqlColumn.table(Tables.MOVIES).column(MovieColumns.TITLE),
      SqlColumn.table(Tables.MOVIES).column(MovieColumns.WATCHED),
      SqlColumn.table(Tables.MOVIES).column(MovieColumns.IN_COLLECTION),
      SqlColumn.table(Tables.MOVIES).column(MovieColumns.IN_WATCHLIST),
      SqlColumn.table(Tables.MOVIES).column(MovieColumns.WATCHING),
      SqlColumn.table(Tables.MOVIES).column(MovieColumns.CHECKED_IN),
      SqlColumn.table(Tables.MOVIES).column(LastModifiedColumns.LAST_MODIFIED),

      SqlColumn.table(Tables.PEOPLE).column(PersonColumns.HEADSHOT),
      SqlColumn.table(Tables.PEOPLE).column(PersonColumns.NAME),
      SqlColumn.table(Tables.PEOPLE).column(LastModifiedColumns.LAST_MODIFIED),
  };

  @Override public Loader<SimpleCursor> onCreateLoader(int id, Bundle args) {
    SimpleCursorLoader loader =
        new SimpleCursorLoader(getActivity(), ProviderSchematic.ListItems.inList(listId),
            PROJECTION, null, null, null);
    loader.setUpdateThrottle(2 * DateUtils.SECOND_IN_MILLIS);
    return loader;
  }

  @Override public void onLoadFinished(Loader loader, SimpleCursor data) {
    setCursor(data);
  }

  @Override public void onLoaderReset(Loader loader) {
    setCursor(null);
  }
}
