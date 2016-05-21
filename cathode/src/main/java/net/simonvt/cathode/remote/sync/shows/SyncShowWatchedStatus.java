/*
 * Copyright (C) 2014 Simon Vig Therkildsen
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

package net.simonvt.cathode.remote.sync.shows;

import android.content.ContentProviderOperation;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.simonvt.cathode.BuildConfig;
import net.simonvt.cathode.api.entity.ShowProgress;
import net.simonvt.cathode.api.service.ShowsService;
import net.simonvt.cathode.jobqueue.JobFailedException;
import net.simonvt.cathode.provider.DatabaseContract;
import net.simonvt.cathode.provider.EpisodeDatabaseHelper;
import net.simonvt.cathode.provider.ProviderSchematic;
import net.simonvt.cathode.provider.SeasonDatabaseHelper;
import net.simonvt.cathode.provider.ShowDatabaseHelper;
import net.simonvt.cathode.remote.CallJob;
import net.simonvt.cathode.remote.Flags;
import retrofit2.Call;
import timber.log.Timber;

public class SyncShowWatchedStatus extends CallJob<ShowProgress> {

  @Inject transient ShowsService showsService;

  @Inject transient ShowDatabaseHelper showHelper;
  @Inject transient SeasonDatabaseHelper seasonHelper;
  @Inject transient EpisodeDatabaseHelper episodeHelper;

  private long traktId;

  public SyncShowWatchedStatus(long traktId) {
    super(Flags.REQUIRES_AUTH);
    this.traktId = traktId;
  }

  @Override public String key() {
    return "SyncShowWatchedStatus" + "&traktId=" + traktId;
  }

  @Override public int getPriority() {
    return PRIORITY_USER_DATA;
  }

  @Override public Call<ShowProgress> getCall() {
    return showsService.getWatchedProgress(traktId);
  }

  @Override public void handleResponse(ShowProgress progress) {
    ShowDatabaseHelper.IdResult showResult = showHelper.getIdOrCreate(traktId);
    final long showId = showResult.showId;
    final boolean didShowExist = !showResult.didCreate;
    if (showResult.didCreate) {
      queue(new SyncShow(traktId));
    }

    List<ShowProgress.Season> seasons = progress.getSeasons();

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();

    for (ShowProgress.Season season : seasons) {
      final int seasonNumber = season.getNumber();

      List<ShowProgress.Episode> episodes = season.getEpisodes();
      for (ShowProgress.Episode episode : episodes) {
        SeasonDatabaseHelper.IdResult seasonResult =
            seasonHelper.getIdOrCreate(showId, seasonNumber);
        final long seasonId = seasonResult.id;
        final boolean didSeasonExist = !seasonResult.didCreate;
        if (seasonResult.didCreate) {
          if (didShowExist) {
            queue(new SyncShow(traktId, true));
          }
        }

        final int episodeNumber = episode.getNumber();
        EpisodeDatabaseHelper.IdResult episodeResult =
            episodeHelper.getIdOrCreate(showId, seasonId, episodeNumber);
        final long episodeId = episodeResult.id;

        if (episodeResult.didCreate) {
          if (didSeasonExist && didShowExist) {
            queue(new SyncSeason(traktId, seasonNumber));
          }
        }

        ContentProviderOperation.Builder builder =
            ContentProviderOperation.newUpdate(ProviderSchematic.Episodes.withId(episodeId));
        builder.withValue(DatabaseContract.EpisodeColumns.WATCHED, episode.getCompleted());
        ops.add(builder.build());
      }
    }

    try {
      getContentResolver().applyBatch(BuildConfig.PROVIDER_AUTHORITY, ops);
    } catch (RemoteException e) {
      Timber.e(e, "SyncShowWatchedStatus failed");
      throw new JobFailedException(e);
    } catch (OperationApplicationException e) {
      Timber.e(e, "SyncShowWatchedStatus failed");
      throw new JobFailedException(e);
    }
  }
}
