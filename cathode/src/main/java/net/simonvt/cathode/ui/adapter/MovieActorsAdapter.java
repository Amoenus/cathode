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

package net.simonvt.cathode.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import net.simonvt.cathode.R;
import net.simonvt.cathode.provider.DatabaseContract.MovieCastColumns;
import net.simonvt.cathode.provider.DatabaseContract.PersonColumns;
import net.simonvt.cathode.provider.DatabaseSchematic;
import net.simonvt.cathode.widget.CircleTransformation;
import net.simonvt.cathode.widget.RemoteImageView;

public class MovieActorsAdapter extends RecyclerCursorAdapter<MovieActorsAdapter.ViewHolder> {

  public static final String[] PROJECTION = new String[] {
      DatabaseSchematic.Tables.MOVIE_CAST + "." + MovieCastColumns.ID,
      DatabaseSchematic.Tables.MOVIE_CAST + "." + MovieCastColumns.PERSON_ID,
      DatabaseSchematic.Tables.MOVIE_CAST + "." + MovieCastColumns.CHARACTER,
      DatabaseSchematic.Tables.PEOPLE + "." + PersonColumns.NAME,
      DatabaseSchematic.Tables.PEOPLE + "." + PersonColumns.HEADSHOT,
  };

  public MovieActorsAdapter(Context context) {
    super(context);
  }

  public MovieActorsAdapter(Context context, Cursor cursor) {
    super(context, cursor);
  }

  @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    View view = LayoutInflater.from(getContext()).inflate(R.layout.item_person, parent, false);
    ViewHolder holder = new ViewHolder(view);
    holder.headshot.addTransformation(new CircleTransformation());
    return holder;
  }

  @Override protected void onBindViewHolder(ViewHolder holder, Cursor cursor, int position) {
    holder.headshot.setImage(cursor.getString(cursor.getColumnIndex(PersonColumns.HEADSHOT)));
    holder.name.setText(cursor.getString(cursor.getColumnIndex(PersonColumns.NAME)));
    holder.job.setText(cursor.getString(cursor.getColumnIndex(MovieCastColumns.CHARACTER)));
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {

    @InjectView(R.id.headshot) RemoteImageView headshot;
    @InjectView(R.id.person_name) TextView name;
    @InjectView(R.id.person_job) TextView job;

    public ViewHolder(View itemView) {
      super(itemView);
      ButterKnife.inject(this, itemView);
    }
  }
}
