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
package net.simonvt.cathode.remote.action;

import javax.inject.Inject;
import net.simonvt.cathode.api.body.DismissBody;
import net.simonvt.cathode.api.service.RecommendationsService;
import net.simonvt.cathode.remote.TraktTask;
import retrofit.RetrofitError;

public class DismissShowRecommendation extends TraktTask {

  @Inject transient RecommendationsService recommendationsService;

  private int tvdbId;

  public DismissShowRecommendation(int tvdbId) {
    this.tvdbId = tvdbId;
  }

  @Override protected void doTask() {
    try {
      recommendationsService.dismissShow(new DismissBody().tvdbId(tvdbId));

      postOnSuccess();
    } catch (RetrofitError e) {
      e.printStackTrace();
      postOnFailure();
    }
  }
}
