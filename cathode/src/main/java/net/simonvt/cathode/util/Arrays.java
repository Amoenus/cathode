/*
 * Copyright (C) 2016 Simon Vig Therkildsen
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

package net.simonvt.cathode.util;

import java.util.List;

public final class Arrays {

  private Arrays() {
  }

  public static long[] toPrimitiveLongArray(List<Long> longList) {
    final int size = longList.size();
    long[] longs = new long[longList.size()];

    for (int i = 0; i < size; i++) {
      longs[i] = longList.get(i);
    }

    return longs;
  }
}
