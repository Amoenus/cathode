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

package net.simonvt.cathode.event;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.simonvt.cathode.util.MainHandler;

public final class CheckInFailedEvent {

  public interface OnCheckInFailedListener {

    void onCheckInFailed(CheckInFailedEvent event);
  }

  private static final List<WeakReference<OnCheckInFailedListener>> LISTENERS = new ArrayList<>();

  public static void registerListener(OnCheckInFailedListener listener) {
    synchronized (LISTENERS) {
      LISTENERS.add(new WeakReference<>(listener));
    }
  }

  public static void unregisterListener(OnCheckInFailedListener listener) {
    synchronized (LISTENERS) {
      for (int i = LISTENERS.size() - 1; i >= 0; i--) {
        WeakReference<OnCheckInFailedListener> ref = LISTENERS.get(i);
        OnCheckInFailedListener l = ref.get();
        if (l == null || l == listener) {
          LISTENERS.remove(ref);
        }
      }
    }
  }

  public static void post(final String title) {
    MainHandler.post(new Runnable() {
      @Override public void run() {
        synchronized (LISTENERS) {
          CheckInFailedEvent event = new CheckInFailedEvent(title);

          for (int i = LISTENERS.size() - 1; i >= 0; i--) {
            WeakReference<OnCheckInFailedListener> ref = LISTENERS.get(i);
            OnCheckInFailedListener l = ref.get();
            if (l == null) {
              LISTENERS.remove(ref);
              continue;
            }

            l.onCheckInFailed(event);
          }
        }
      }
    });
  }

  public String title;

  private CheckInFailedEvent(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }
}
