package com.dotorimaru.land.util;

/**
 * 격자 스냅 유틸. 같은 크기 토지들이 타일처럼 맞물리도록 최소 좌표를 격자선에 정렬합니다.
 */
public final class Grid {

    private Grid() {
    }

    /** coord 가 속한 격자 셀의 시작 좌표(격자선). */
    public static int snapMin(int coord, int size) {
        if (size <= 1) return coord;
        return Math.floorDiv(coord, size) * size;
    }
}
