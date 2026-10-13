/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        // KCon 品牌色，取自 logo 主色（红）与深黑底
        brand: {
          DEFAULT: '#E23B2E',   // 主红：按钮、数字、强调
          light: '#FF6A5A',     // 亮红：hover、发光
          dark: '#8C1A14'       // 深红：按下、暗部
        },
        // 把 Tailwind 默认的 slate（偏蓝灰）覆盖为中性灰。
        // 原因：代码里大量使用 slate-800/700 做卡片与边框底色，
        // 蓝调在红黑主题下会显脏。覆盖色阶后所有 slate-N 写法不用改代码。
        slate: {
          900: '#131316',
          800: '#1C1C21',
          700: '#2A2A31',
          600: '#3A3A42',
          500: '#6B6B75',
          400: '#9A9AA4',
          300: '#C9C9D1',
          200: '#E2E2E7',
          100: '#F0F0F3'
        }
      },
      fontFamily: {
        num: ['DIN Alternate', 'Impact', 'Arial Black', 'sans-serif']
      }
    }
  },
  plugins: []
}
