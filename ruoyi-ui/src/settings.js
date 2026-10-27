module.exports = {
  /**
   * 网页标题
   */
  title: process.env.VUE_APP_TITLE,

  /**
   * 侧边栏主题 深色主题theme-dark，浅色主题theme-light
   */
  sideTheme: 'theme-dark',

  /**
   * 系统布局配置
   * showSettings: 右上角设置齿轮（主题切换/布局设置）
   * KCon 现场系统布局与配色固定，现场不需要临时改主题，
   * 且若依的设置面板会暴露框架痕迹，故关闭
   */
  showSettings: false,

  /**
   * 菜单导航模式 1、纯左侧 2、混合（左侧+顶部） 3、纯顶部
   */
  navType: 1,

  /**
   * 是否显示 tagsView
   * KCon 现场菜单是平铺的固定几项，不需要页签栏做多任务切换，关闭以腾出纵向空间
   */
  tagsView: false,

  /**
   * 持久化标签页
   */
  tagsViewPersist: false,

  /**
   * 显示页签图标
   */
  tagsIcon: false,

  /**
   * 是否固定头部
   */
  fixedHeader: true,

  /**
   * 是否显示logo
   */
  sidebarLogo: true,

  /**
   * 是否显示动态标题
   */
  dynamicTitle: false,

  /**
   * 是否显示底部版权
   */
  footerVisible: false,

  /**
   * 底部版权文本内容
   */
  footerContent: 'KCon 2026 · 签到抽奖系统'
}
