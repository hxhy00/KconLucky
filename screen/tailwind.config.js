/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        // KCon 品牌色：主色跟随 logo 的红，中奖态保留金色（金=中奖，属语义色）
        brand: {
          DEFAULT: '#E23B2E',
          light: '#FF6A5A',
          dark: '#8C1A14'
        },
        gold: '#FFD166',
        // slate 默认偏蓝，在红黑主题下会显脏，覆盖为中性灰（300-900 + 200）
        slate: {
          900: '#131316',
          800: '#1C1C21',
          700: '#2A2A31',
          600: '#3A3A42',
          500: '#6B6B75',
          400: '#9A9AA4',
          300: '#C9C9D1',
          200: '#DDDDE3'
        }
      },
      fontFamily: {
        num: ['DIN Alternate', 'Impact', 'Arial Black', 'sans-serif']
      }
    }
  },
  plugins: []
}