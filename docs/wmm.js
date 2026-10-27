/* WMM2025 offline, adapted from dpyeates/magvar (MIT license).
 * License: https://github.com/dpyeates/magvar/blob/master/LICENSE
 * Magnetic declination: positive east. Model epoch 2025 to 2030.
 */
"use strict";
(function(){
const deg2rad = degrees => degrees*Math.PI/180;
const rad2deg = radians => radians*180/Math.PI;
const roundToDecimalPlace = (v,d)=>Math.round(v*10**d)/10**d;
const zeroArray2D = (rows,cols)=>Array.from({length:rows},()=>Array(cols).fill(0));
const gregorianToJulian = (year,month,day) => (Date.UTC(year,month,day)/86400000) + 2440587.5;
// WMM _2025 coefficients
const gnmWmm = [
  [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [-29351.8, -1410.8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [-2556.6, 2951.1, 1649.3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [1361.0, -2404.1, 1243.8, 453.6, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [895.0, 799.5, 55.7, -281.1, 12.1, 0, 0, 0, 0, 0, 0, 0, 0],
  [-233.2, 368.9, 187.2, -138.7, -142.0, 20.9, 0, 0, 0, 0, 0, 0, 0],
  [64.4, 63.8, 76.9, -115.7, -40.9, 14.9, -60.7, 0, 0, 0, 0, 0, 0],
  [79.5, -77.0, -8.8, 59.3, 15.8, 2.5, -11.1, 14.2, 0, 0, 0, 0, 0],
  [23.2, 10.8, -17.5, 2.0, -21.7, 16.9, 15.0, -16.8, 0.9, 0, 0, 0, 0],
  [4.6, 7.8, 3.0, -0.2, -2.5, -13.1, 2.4, 8.6, -8.7, -12.9, 0, 0, 0],
  [-1.3, -6.4, 0.2, 2.0, -1.0, -0.6, -0.9, 1.5, 0.9, -2.7, -3.9, 0, 0],
  [2.9, -1.5, -2.5, 2.4, -0.6, -0.1, -0.6, -0.1, 1.1, -1.0, -0.2, 2.6, 0],
  [-2.0, -0.2, 0.3, 1.2, -1.3, 0.6, 0.6, 0.5, -0.1, -0.4, -0.2, -1.3, -0.7]
];

const hnmWmm = [
  [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, 4545.4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, -3133.6, -815.1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, -56.6, 237.5, -549.5, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, 278.6, -133.9, 212.0, -375.6, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, 45.4, 220.2, -122.9, 43.0, 106.1, 0, 0, 0, 0, 0, 0, 0],
  [0, -18.4, 16.8, 48.8, -59.8, 10.9, 72.7, 0, 0, 0, 0, 0, 0],
  [0, -48.9, -14.4, -1.0, 23.4, -7.4, -25.1, -2.3, 0, 0, 0, 0, 0],
  [0, 7.1, -12.6, 11.4, -9.7, 12.7, 0.7, -5.2, 3.9, 0, 0, 0, 0],
  [0, -24.8, 12.2, 8.3, -3.3, -5.2, 7.2, -0.6, 0.8, 10.0, 0, 0, 0],
  [0, 3.3, 0.0, 2.4, 5.3, -9.1, 0.4, -4.2, -3.8, 0.9, -9.1, 0, 0],
  [0, 0, 2.9, -0.6, 0.2, 0.5, -0.3, -1.2, -1.7, -2.9, -1.8, -2.3, 0],
  [0, -1.3, 0.7, 1.0, -1.4, 0.0, 0.6, -0.1, 0.8, 0.1, -1.0, 0.1, 0.2]
];

const gtnmWmm = [
  [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [12.0, 9.7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [-11.6, -5.2, -8.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [-1.3, -4.2, 0.4, -15.6, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [-1.6, -2.4, -6.0, 5.6, -7.0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0.6, 1.4, 0.0, 0.6, 2.2, 0.9, 0, 0, 0, 0, 0, 0, 0],
  [-0.2, -0.4, 0.9, 1.2, -0.9, 0.3, 0.9, 0, 0, 0, 0, 0, 0],
  [-0, -0.1, -0.1, 0.5, -0.1, -0.8, -0.8, 0.8, 0, 0, 0, 0, 0],
  [-0.1, 0.2, 0.0, 0.5, -0.1, 0.3, 0.2, -0, 0.2, 0, 0, 0, 0],
  [-0, -0.1, 0.1, 0.3, -0.3, 0, 0.3, -0.1, 0.1, -0.1, 0, 0, 0],
  [0.1, 0.0, 0.1, 0.1, -0, -0.3, 0, -0.1, -0.1, -0, -0, 0, 0],
  [0, -0, 0, 0, 0, -0.1, 0, -0, -0.1, -0.1, -0.1, -0.1, 0],
  [0, 0, -0, -0, -0, -0, 0.1, -0, 0, 0, -0.1, -0, -0.1]
];

const htnmWmm = [
  [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, -21.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, -27.7, -12.1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, 4.0, -0.3, -4.1, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, -1.1, 4.1, 1.6, -4.4, 0, 0, 0, 0, 0, 0, 0, 0],
  [0, -0.5, 2.2, 0.4, 1.7, 1.9, 0, 0, 0, 0, 0, 0, 0],
  [0, 0.3, -1.6, -0.4, 0.9, 0.7, 0.9, 0, 0, 0, 0, 0, 0],
  [0, 0.6, 0.5, -0.8, 0.0, -1.0, 0.6, -0.2, 0, 0, 0, 0, 0],
  [0, -0.2, 0.5, -0.4, 0.4, -0.5, -0.6, 0.3, 0.2, 0, 0, 0, 0],
  [0, -0.3, 0.3, -0.3, 0.3, 0.2, -0.1, -0.2, 0.4, 0.1, 0, 0, 0],
  [0, 0, -0, -0.2, 0.1, -0.1, 0.1, 0.0, -0.1, 0.2, -0, 0, 0],
  [0, -0, 0.1, -0, 0.1, -0, -0, 0.1, -0, 0, 0, 0, 0],
  [0, -0, 0, -0.1, 0.1, -0, -0, -0, 0, -0, -0, 0, -0.1]
];

const julianDaysCOF = 2460677;




const globe = {
  a: 6378.137, // semi-major axis [equatorial radius] of WGS84 ellipsoid
  b: 6356.7523142, // semi-minor axis referenced to the WGS84 ellipsoid
  r0: 6371.2 // "mean radius" for spherical harmonic expansion
};

const P = zeroArray2D(13, 13);
const DP = zeroArray2D(13, 13);
const gnm = zeroArray2D(13, 13);
const hnm = zeroArray2D(13, 13);
const sm = new Float32Array(13);
const cm = new Float32Array(13);
const root = new Float32Array(13);
const roots = zeroArray2D(13, 13).map(row => row.map(_ => new Float32Array(2)));

for (let n = 2; n <= 12; n++) {
  root[n] = Math.sqrt((2.0 * n - 1) / (2.0 * n));
}

for (let m = 0; m <= 12; m++) {
  const mm = m * m;
  for (let n = Math.max(m + 1, 2); n <= 12; n++) {
    roots[m][n][0] = Math.sqrt((n - 1) * (n - 1) - mm);
    roots[m][n][1] = 1.0 / Math.sqrt(n * n - mm);
  }
}

const d = new Date();
const julianDaysNow = gregorianToJulian(d.getFullYear(),d.getMonth(),d.getDate());

/**
 * magvar
 * Given a latitude and longitude position and optional height in metres above mean sea level,
 * return magnetic variation in degrees for the current date.
 * N and E latitude and longitude are positive values, S and W negative.
 * @param {number} latitude the latitude in degrees of the point we want to obtain the magnetic variation.
 * @param {number} longitude the longitude in degrees of the point we want to obtain the magnetic variation.
 * @param {number} altitude the height in km above mean sea level of the point we want to obtain the magnetic variation.
 * @returns {number} magnetic variation at the given coordinates and height for the current date.
 */
const magvar = (latitude, longitude, altitude = 0) => calculateMagVar(julianDaysNow, latitude, longitude, altitude);

/**
 * calculateMagVar
 * Given a date in julian days, latitude, longitude and height, return variation (in degrees)
 * N and E latitude and longitude are positive values, South and West negative.
 * @param {number} julianDays the current number of days since epoch.
 * @param {number} latitude the latitude in degrees of the point we want to obtain the magnetic variation.
 * @param {number} longitude the longitude in degrees of the point we want to obtain the magnetic variation.
 * @param {number} altitude the height in km above mean sea level of the point we want to obtain the magnetic variation.
 * @returns {number} magnetic variation at the given coordinates and height.
 */
const calculateMagVar = (julianDays, latitude, longitude, altitude = 0) => {
  const latRad = deg2rad(latitude);
  const lonRad = deg2rad(longitude);
  const sinLat = Math.sin(latRad);
  const cosLat = Math.cos(latRad);
  const sr = Math.sqrt(globe.a ** 2 * cosLat ** 2 + globe.b ** 2 * sinLat ** 2);
  const theta = Math.atan2(cosLat * (altitude * sr + globe.a ** 2), sinLat * (altitude * sr + globe.b ** 2));
  const r = Math.sqrt(
    altitude ** 2 +
    2 * altitude * sr +
    (globe.a ** 4 - (globe.a ** 4 - globe.b ** 4) * sinLat ** 2) /
    (globe.a ** 2 - (globe.a ** 2 - globe.b ** 2) * sinLat ** 2)
  );
  const c = Math.cos(theta);
  const s = Math.sin(theta);
  const invS = 1 / (s + (s === 0 ? 1e-8 : 0));

  P[0][0] = 1.0;
  P[1][1] = s;
  DP[0][0] = 0.0;
  DP[1][1] = c;
  P[1][0] = c;
  DP[1][0] = -s;

  for (let n = 2; n <= 12; n++) {
    P[n][n] = P[n - 1][n - 1] * s * root[n];
    DP[n][n] = (DP[n - 1][n - 1] * s + P[n - 1][n - 1] * c) * root[n];
  }

  for (let m = 0; m <= 12; m++) {
    for (let n = Math.max(m + 1, 2); n <= 12; n++) {
      P[n][m] = (P[n - 1][m] * c * (2 * n - 1) - P[n - 2][m] * roots[m][n][0]) * roots[m][n][1];
      DP[n][m] = ((DP[n - 1][m] * c - P[n - 1][m] * s) * (2 * n - 1) - DP[n - 2][m] * roots[m][n][0]) *
        roots[m][n][1];
    }
  }

  const yearFrac = (julianDays - julianDaysCOF) / 365.25;
  for (let n = 1; n <= 12; n++) {
    for (let m = 0; m <= 12; m++) {
      gnm[n][m] = gnmWmm[n][m] + yearFrac * gtnmWmm[n][m];
      hnm[n][m] = hnmWmm[n][m] + yearFrac * htnmWmm[n][m];
    }
  }

  for (let m = 0; m <= 12; m++) {
    sm[m] = Math.sin(m * lonRad);
    cm[m] = Math.cos(m * lonRad);
  }

  let BR = 0.0;
  let BTheta = 0.0;
  let BPhi = 0.0;
  const fn0 = globe.r0 / r;
  let fn = fn0 ** 2;

  for (let n = 1; n <= 12; n++) {
    let c1n = 0;
    let c2n = 0;
    let c3n = 0;
    for (let m = 0; m <= n; m++) {
      const tmp = gnm[n][m] * cm[m] + hnm[n][m] * sm[m];
      c1n += tmp * P[n][m];
      c2n += tmp * DP[n][m];
      c3n += m * (gnm[n][m] * sm[m] - hnm[n][m] * cm[m]) * P[n][m];
    }
    fn *= fn0;
    BR += (n + 1) * c1n * fn;
    BTheta -= c2n * fn;
    BPhi += c3n * fn * invS;
  }

  const psi = theta - (Math.PI / 2 - latRad);
  const sinPsi = Math.sin(psi);
  const cosPsi = Math.cos(psi);
  const X = -BTheta * cosPsi - BR * sinPsi;
  const Y = BPhi;

  return X !== 0.0 || Y !== 0.0 ?
    roundToDecimalPlace(rad2deg(Math.atan2(Y, X)), 2) :
    0.0;
};


window.offlineDeclination = (latitude,longitude,date=new Date()) => {
 const days=gregorianToJulian(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate());
 return calculateMagVar(days, latitude, longitude, 0);
};
})();
