/*
 * @(#) $(JCGO)/jtrsrc/com/ivmaisoft/jcgo/JavaVersion.java --
 * a part of JCGO translator.
 **
 * Project: JCGO (http://www.ivmaisoft.com/jcgo/)
 * Copyright (C) 2001-2026 Ivan Maidanski <ivmai@mail.ru>
 * All rights reserved.
 */

/*
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 **
 * This software is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License (GPL) for more details.
 **
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 **
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module. An independent module is a module which is not derived from
 * or based on this library. If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */

package com.ivmaisoft.jcgo;

/**
 * Java source language level codes used by the -source flag.
 * Codes are chosen so that newer levels compare greater than older ones
 * (e.g. JLS_14 &lt; JLS_50). The default level is JLS_14.
 */

final class JavaVersion {

    static final int JLS_12 = 12;

    static final int JLS_13 = 13;

    static final int JLS_14 = 14;

    static final int JLS_50 = 50;

    static final int JLS_60 = 60;

    static final int JLS_70 = 70;

    static final int JLS_80 = 80;

    static final int JLS_90 = 90;

    static final int JLS_100 = 100;

    static final int JLS_110 = 110;

    static final int JLS_120 = 120;

    static final int JLS_130 = 130;

    static final int JLS_140 = 140;

    static final int JLS_150 = 150;

    static final int JLS_160 = 160;

    static final int JLS_170 = 170;

    static final int JLS_180 = 180;

    static final int JLS_190 = 190;

    static final int JLS_200 = 200;

    static final int JLS_210 = 210;

    static final int DEFAULT = JLS_14;

    private JavaVersion() {
    }

    static String format(int code) {
        if (code < JLS_50) {
            return "1." + Integer.toString(code - 10);
        }
        return Integer.toString(code / 10);
    }

    /**
     * Parses a -source argument into a JLS_* code.
     * Accepts "1.2", "1.3", "1.4", "5", "6", "7", "8" (and "1.5".."1.8").
     * Returns 0 if the argument is not a recognized level.
     */
    static int parseSourceLevel(String value) {
        if (value == null) {
            return 0;
        }
        if (value.equals("1.2")) {
            return JLS_12;
        }
        if (value.equals("1.3")) {
            return JLS_13;
        }
        if (value.equals("1.4")) {
            return JLS_14;
        }
        if (value.equals("5") || value.equals("1.5")) {
            return JLS_50;
        }
        if (value.equals("6") || value.equals("1.6")) {
            return JLS_60;
        }
        if (value.equals("7") || value.equals("1.7")) {
            return JLS_70;
        }
        if (value.equals("8") || value.equals("1.8")) {
            return JLS_80;
        }
        if (value.equals("9")) {
            return JLS_90;
        }
        if (value.equals("10")) {
            return JLS_100;
        }
        if (value.equals("11")) {
            return JLS_110;
        }
        if (value.equals("12")) {
            return JLS_120;
        }
        if (value.equals("13")) {
            return JLS_130;
        }
        if (value.equals("14")) {
            return JLS_140;
        }
        if (value.equals("15")) {
            return JLS_150;
        }
        if (value.equals("16")) {
            return JLS_160;
        }
        if (value.equals("17")) {
            return JLS_170;
        }
        if (value.equals("18")) {
            return JLS_180;
        }
        if (value.equals("19")) {
            return JLS_190;
        }
        if (value.equals("20")) {
            return JLS_200;
        }
        if (value.equals("21")) {
            return JLS_210;
        }
        return 0;
    }
}
