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

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import net.simonvt.cathode.R;
import net.simonvt.cathode.provider.CathodeContract;
import net.simonvt.cathode.ui.BaseActivity;
import net.simonvt.cathode.ui.LibraryType;
import net.simonvt.cathode.ui.adapter.ShowsWithNextAdapter;

public class UpcomingShowsFragment extends ShowsFragment {

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle inState) {
    return inflater.inflate(R.layout.fragment_shows_upcoming, container, false);
  }

  @Override public String getTitle() {
    return getResources().getString(R.string.title_shows_upcoming);
  }

  @Override protected LibraryType getLibraryType() {
    return LibraryType.WATCHED;
  }

  @Override protected int getLoaderId() {
    return BaseActivity.LOADER_SHOWS_UPCOMING;
  }

  @Override public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
    final Uri contentUri = CathodeContract.Shows.SHOWS_WITHNEXT_IGNOREWATCHED;
    CursorLoader cl = new CursorLoader(getActivity(), contentUri, ShowsWithNextAdapter.PROJECTION,
        CathodeContract.Shows.WATCHED_COUNT + ">0", null, CathodeContract.Shows.DEFAULT_SORT);
    cl.setUpdateThrottle(2 * DateUtils.SECOND_IN_MILLIS);
    return cl;
  }
}
