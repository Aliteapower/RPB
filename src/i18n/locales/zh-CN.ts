export const zhCN = {
  common: {
    actions: {
      login: '登录',
      loggingIn: '登录中...',
      logout: '退出登录',
      loggingOut: '退出中',
      refresh: '刷新',
      loading: '加载中',
      query: '查询',
      reset: '重置',
      previousPage: '上一页',
      nextPage: '下一页',
      saving: '保存中',
      save: '保存',
      edit: '编辑',
      upload: '上传',
      clear: '清空',
      delete: '删除',
      restore: '恢复',
      add: '新增',
      close: '关闭',
      cancel: '取消'
    },
    dateTime: {
      date: '日期',
      time: '时间',
      year: '年',
      month: '月',
      day: '日',
      hour: '时',
      minute: '分'
    },
    pagination: {
      summary: '第 {start}-{end} 条 / 共 {total} 条'
    },
    password: {
      show: '显示密码',
      hide: '隐藏密码'
    },
    localeSwitcher: {
      aria: '语言切换',
      zhCN: '中文',
      enSG: 'EN'
    },
    product: {
      reservationQueueCallSystem: '预约排队叫号系统'
    }
  },
  i18nCatalog: {
    namespaces: {
      reason: '原因文案',
      status: '状态文案',
      public_booking: '公开预约',
      reservation_share: '预约分享',
      queue: '排队',
      call_screen: '叫号大屏',
      reservation_meal_period: '预约餐段'
    },
    categories: {
      cancellation: '取消原因',
      no_show: '爽约原因',
      queue: '排队',
      table: '桌台',
      cleaning: '清台',
      reservation: '预约',
      prompt: '提示文案',
      template: '模板',
      display: '展示文案',
      display_name: '展示名称',
      restaurant_default: '餐厅默认'
    },
    textKinds: {
      label: '标签',
      template: '模板',
      status: '状态',
      prompt: '提示文案'
    }
  },
  components: {
    callScreenAdModeSwitch: {
      text: '文案轮播',
      media: '图片/视频轮播',
      aria: '轮播类型'
    },
    downloadableQrCode: {
      title: '二维码',
      aria: '二维码下载',
      download: '下载二维码',
      rendering: '生成中',
      errors: {
        empty: '二维码内容为空',
        renderFailed: '二维码生成失败'
      }
    }
  },
  nav: {
    platform: {
      aria: '平台后台导航',
      title: '平台后台',
      tenants: '租户管理',
      billing: '租户计费',
      profile: '平台资料',
      productLines: '产品线',
      i18nCatalog: '国际化字典',
      callScreenSeed: '叫号模板',
      mealPeriodSeed: '预约餐段',
      shareTemplateSeed: '预约确认模板'
    },
    tenant: {
      aria: '租户后台导航',
      title: '租户后台',
      storePrefix: '门店',
      profile: '租户资料',
      staff: '员工管理',
      customers: '顾客管理',
      tables: '桌号管理',
      settings: '基础设置',
      i18nCatalog: '国际化字典',
      shareTemplate: '订位分享',
      publicBooking: '公网预约',
      callScreen: '叫号屏配置'
    },
    staff: {
      aria: '员工工作台导航',
      home: '首页',
      reservation: '预约',
      queue: '排队',
      table: '桌台'
    }
  },
  login: {
    shellAria: '后台登录',
    heading: '后台入口',
    entryTabAria: '选择后台入口',
    passwordPolicy: '密码为 6 位数字或英文字母',
    fields: {
      tenantCode: '租户代码',
      employeeUsername: '员工账号',
      password: '密码'
    },
    remember: {
      account: '记住账号'
    },
    captcha: {
      aria: '滑块校验',
      refresh: '换一张',
      loading: '加载中'
    },
    entries: {
      platformAdmin: {
        tab: '平台',
        title: '平台后台',
        description: '创建租户、开通租户后台和平台级管理。',
        accountLabel: '平台账号',
        targetHint: '平台范围'
      },
      tenantAdmin: {
        tab: '租户',
        title: '租户后台',
        description: '设置店面、维护员工账号和授权范围。',
        accountLabel: '租户账号',
        targetHint: '租户 {tenantCode}'
      },
      tenantStaff: {
        tab: '员工',
        title: '租户员工',
        description: '进入已授权店面，租户子域下无需再输入租户代码。',
        accountLabel: '员工账号',
        targetHint: '租户 {tenantCode} / 员工 {employeeUsername}'
      }
    },
    store: {
      authorized: '授权店面',
      authorizedDescription: '登录后直接进入当前域名或账号默认店面，可在工作台切换店面'
    },
    errors: {
      captchaLoadFailed: '滑块校验加载失败',
      missingStoreScope: '账号未绑定门店，请联系平台管理员完成租户初始化',
      loginFailed: '登录失败',
      captchaMismatch: '滑块校验未通过',
      invalidCredentials: '账号或密码不正确'
    }
  },
  appGate: {
    errors: {
      tenantAppNotEnabled: {
        title: '预约排队叫号系统未开通',
        message: '请联系平台管理员在租户计费中勾选产品线后再使用。'
      },
      tenantAppExpired: {
        title: '产品线订阅已到期',
        message: '请联系平台管理员续费预约排队叫号系统后再使用。'
      },
      storeAppNotEnabled: {
        title: '当前门店未启用',
        message: '请联系管理员检查门店的预约排队叫号系统配置。'
      },
      permissionDenied: {
        title: '当前账号没有此功能权限',
        message: '请联系管理员调整员工权限后再使用。'
      },
      appDisabled: {
        title: '产品线暂不可用',
        message: '请联系平台管理员检查产品线状态。'
      },
      loadFailed: {
        title: '加载失败',
        message: '请稍后重试。'
      }
    }
  },
  storeSwitcher: {
    label: '切换店面',
    unknown: '当前门店'
  },
  reservationCreate: {
    errors: {
      startInPast: '预约开始时间不能早于当前时间，请选择稍后的时间。',
      invalidPartySize: '人数必须大于 0。',
      invalidTimeRange: '预约时间不正确，请重新选择时间。',
      timeSlotUnavailable: '请选择门店餐段内的可预约时间。',
      invalidPhoneE164: '手机号必须是 8 位新加坡号码。',
      requestFailed: '预约创建失败，请稍后重试。',
      networkFailure: '网络连接失败，请检查后重试。',
      forbidden: '当前账号没有创建预约权限。',
      customerNotFound: '未找到对应顾客，请检查手机号或重新填写。',
      duplicateActive: '该顾客已有进行中的预约。',
      capacityInsufficient: '当前时段容量不足，请调整时间或人数。'
    }
  },
  reservationPublicShare: {
    errors: {
      expired: '链接已失效',
      notFound: '预约信息不存在',
      loadFailed: '预约信息读取失败'
    }
  },
  reservationShareTemplatePreview: {
    variables: {
      storeName: '示例门店',
      reservationNo: 'R-EXAMPLE-0001',
      reservationCode: 'R-EXAMPLE-0001',
      reservationDate: '15-07-2026 (星期三)',
      reservationTime: '19:30',
      reservedStartAt: '15-07-2026 (星期三) 19:30',
      partySize: '2',
      tableCode: 'A01',
      holdMinutes: '15',
      contactName: '示例顾客',
      guestSalutation: '先生',
      maskedPhone: '0000****',
      storeAddress: '示例地址 1 号',
      googleMapUrl: 'https://example.com/map',
      storePhone: '0000 0000',
      arrivalNote: '请提前 10 分钟到店',
      confirmInstruction: '回复确认可保留订位',
      cancelInstruction: '如需取消，请提前 2 小时联系我们',
      changeInstruction: '如需修改人数或时间，请致电门店',
      replyInstruction: '收到请回复确认'
    }
  },
  reservationWorkbench: {
    statuses: {
      confirmed: '已预约',
      arrived: '已到店',
      seated: '已入桌',
      cancelled: '已取消',
      noShow: '爽约',
      completed: '已完成',
      draft: '草稿'
    },
    queueStatuses: {
      waiting: '排队中',
      called: '已叫号',
      skipped: '已过号',
      rejoined: '已重回',
      seated: '已入座',
      cancelled: '排队已取消',
      expired: '排队已过期',
      queued: '已排队'
    },
    share: {
      prepared: '已准备链接',
      aria: '订位链接转发',
      loading: '读取中',
      whatsapp: 'WhatsApp发送',
      wechat: '微信发送',
      system: '系统转发',
      copy: '复制链接',
      linkAria: '订位分享链接',
      noForwardLink: '暂无可转发链接',
      copied: '链接已复制',
      nativeOpened: '已打开系统转发',
      systemFallbackCopied: '系统转发不可用，链接已复制',
      manualShare: '当前浏览器限制转发，请手动复制下方链接',
      phoneUnavailable: '顾客手机号暂不可用于 WhatsApp',
      phoneMissing: '顾客未填写可用手机号',
      whatsappOpening: '将以 {sender} 身份提示打开 WhatsApp',
      defaultSender: '门店',
      noText: '暂无可发送文案',
      manualTextCopy: '当前浏览器限制复制，请手动复制文案后打开微信',
      wechatOpening: '文案已复制，正在打开微信',
      noCopyLink: '暂无可复制链接',
      manualCopyLink: '当前浏览器限制复制，请手动复制下方链接',
      loadFailed: '订位信息读取失败'
    },
    tableAssignment: {
      action: '指定桌号',
      aria: '为预约指定桌号',
      title: '指定桌号',
      close: '关闭指定桌号',
      currentReservation: '当前预约',
      subject: '为 {customer}（{count}人）预留桌号',
      instruction: '仅显示容量合适且在预约时段未被占用的桌号。指定后可通过现有分享按钮发送给客户。',
      loading: '正在读取可指定桌号…',
      empty: '暂无可指定桌号',
      emptyHint: '可以稍后刷新，或检查桌台容量与同一时段的预约。',
      unassignedArea: '未分区',
      capacity: '容纳 {min}-{max} 人',
      selected: '已选择：{code}',
      selectPrompt: '请选择一个桌号',
      confirm: '确认指定',
      submitting: '指定中…',
      refresh: '刷新可选桌号',
      conflict: '该桌号刚被占用或预约状态已变化，列表已刷新，请重新选择。',
      forbidden: '当前账号没有指定桌号权限。',
      loadOrSubmitFailed: '暂时无法指定桌号，请刷新后重试。',
      errorCode: '错误代码：{code}'
    },
    todayList: {
      aria: '当日预约',
      title: '当日预约',
      total: '共 {count} 条',
      statusFilterAria: '状态筛选',
      filtersAria: '预约筛选',
      phone: '手机号',
      partySize: '人数',
      allPartySizes: '全部人数',
      partySizeOption: '{size}人',
      loadingTitle: '加载中...',
      loadingDescription: '正在读取当前门店预约。',
      loadFailedTitle: '加载失败',
      loadFailedMessage: '暂时无法读取当日预约，请稍后重试。',
      cancelFailedTitle: '取消失败',
      cancelFailedMessage: '暂时无法取消预约，请稍后重试。',
      statusActionFailedTitle: '状态操作失败',
      statusActionFailedMessage: '暂时无法更新预约状态，请稍后重试。',
      emptyTitle: '今日暂无预约',
      emptyDescription: '可以切换日期或状态筛选。',
      filteredEmptyTitle: '没有匹配的预约',
      filteredEmptyDescription: '可以重置筛选后再查看。',
      itemsAria: '今日预约列表'
    },
    item: {
      currentDayOnly: '仅当日预约可以操作',
      seatFromQueue: '排队入座',
      seatDirect: '入桌',
      jumping: '跳转中',
      seating: '入桌中',
      completed: '已完成',
      seated: '已入桌',
      reservationAssigned: '预约指定',
      tableAssigned: '{resource}：{code}（{label}）',
      tableUnassigned: '桌台：未指定',
      tableSeated: '桌台：已入桌',
      unset: '未填写',
      queueTicket: '排队票',
      tableGroup: '桌组',
      diningTable: '桌号',
      tableResource: '桌台',
      partySizeSummary: '{count}人 · {table}',
      actionsAria: '预约操作',
      hasNote: '有备注',
      hideNote: '收起备注',
      noteLabel: '预约备注',
      checkIn: '到店',
      checkingIn: '到店中',
      noShow: '爽约',
      noShowing: '爽约中',
      cancelTitle: '取消预约',
      cancelling: '取消中'
    },
    quickActions: {
      aria: '预约管理',
      title: '预约管理',
      createReservation: '创建预约',
      createReservationSymbol: '约',
      createDisabledTitle: '过去日期不可创建预约',
      walkInQueue: '现场取号',
      walkInQueueSymbol: '取',
      reservationToQueue: '预约转排队',
      reservationToQueueSymbol: '排'
    },
    monthCalendar: {
      aria: '预约日历',
      monthTitle: '{year}年{month}月',
      selectedPrefix: '已选择',
      selectPrefix: '选择',
      reservationCount: '，{count} 个预订',
      noReservation: '，暂无预订',
      dateLimit: '，不可创建新预约',
      previousMonth: '上个月',
      nextMonth: '下个月',
      weekdays: {
        sunday: '日',
        monday: '一',
        tuesday: '二',
        wednesday: '三',
        thursday: '四',
        friday: '五',
        saturday: '六'
      }
    },
    dialogs: {
      currentReservation: '当前预约',
      unknownTable: '当前桌号：未识别',
      currentTable: '当前桌号：{code}',
      seatAria: '选择桌号入桌弹窗',
      seatTitle: '选择桌号（入桌）',
      seatClose: '关闭选择桌号',
      seatSubject: '为 {customer}（预约）分配座位',
      switchAria: '选择桌号换桌弹窗',
      switchTitle: '选择桌号（换桌）',
      switchClose: '关闭换桌',
      switchSubject: '为 {customer} 更换桌台',
      requiredResource: '预约指定',
      seatFailed: '入桌失败',
      switchFailed: '换桌失败',
      errorCode: '错误代码：{code}',
      messageKey: '消息键：{messageKey}',
      seatSubmitting: '入桌中...',
      switchSubmitting: '换桌中...',
      confirmSeat: '确认入桌',
      confirmSwitch: '确认换桌',
      tableGroupWithCode: '桌组 {code}',
      tableWithCode: '桌号 {code}',
      unassigned: '未指定'
    },
    createDialog: {
      allMealPeriods: '全部',
      unassigned: '未指定',
      loadingTables: '正在读取桌台',
      tableLoadFailed: '桌台列表读取失败',
      chooseTable: '点击选择桌号',
      capacity: '{count}人',
      available: '空闲',
      unavailable: '不可选',
      capacityMismatch: '人数不匹配',
      locked: '已锁定',
      occupied: '已占用',
      cleaning: '清台中',
      reserved: '已预留',
      aria: '新增预约弹窗',
      title: '新增预约',
      closeAria: '关闭新增预约',
      success: '预约已创建',
      successSummary: '{code} · {count}人',
      done: '完成',
      date: '日期',
      time: '时间',
      mealFilterAria: '餐段筛选',
      timeSlotsAria: '预约可选时间',
      nextDay: ' · 次日',
      loadingTime: '正在读取时间',
      timeLoadFailed: '时间列表读取失败',
      noTimeSlots: '暂无可选时间',
      partySize: '人数',
      customerEmail: '电邮（可选）',
      optionalTable: '桌号（可选）',
      tablePickerAria: '选择预约桌号弹窗',
      tablePickerTitle: '选择预约桌号',
      closeTablePicker: '关闭桌号选择',
      currentSelection: '当前选择：{selection}',
      temporaryGroupAria: '预约临时分组',
      temporaryGroup: '临时分组',
      temporarySummary: '{date} · 已选 {count} 张',
      groupName: '组名',
      groupNamePlaceholder: '例如 A区临组1',
      exitSelection: '退出选择',
      composeTables: '组合桌台',
      saveGroup: '保存分组',
      saveGroupSubmitting: '保存中',
      saveSubmitting: '保存中...',
      save: '保存'
    }
  },
  platform: {
    i18nCatalog: {
      page: {
        kicker: '平台配置',
        title: '国际化字典',
        note: '这里只维护平台默认业务文案。登录、导航、按钮、权限错误和后台菜单仍由前端语言包随版本发布。'
      },
      fields: {
        namespaceFilter: '字典命名空间筛选',
        allNamespaces: '全部命名空间',
        noPlaceholders: '无占位符',
        status: '状态',
        empty: '暂无匹配字典'
      },
      status: {
        active: '启用',
        inactive: '停用'
      },
      messages: {
        saved: '平台默认文案已保存',
        noChanges: '没有需要保存的平台文案'
      },
      errors: {
        operationFailed: '操作失败，请稍后重试',
        sessionExpired: '登录已失效，请重新登录',
        forbidden: '没有平台国际化字典权限',
        invalid: '请检查字典内容、语言和状态',
        versionConflict: '字典已被其他操作更新，请刷新后重试',
        placeholderUnknown: '模板包含未授权占位符',
        keyNotAllowed: '该文案不允许在后台维护'
      }
    },
    productLines: {
      page: {
        kicker: '基础设置',
        title: '产品线',
        defaultDisplayName: '预约排队叫号产线',
        defaultDescription: '预约、排队、叫号一体化产线'
      },
      messages: {
        created: '产品线已创建',
        saved: '已保存',
        pricesSaved: '定价已保存'
      },
      errors: {
        operationFailed: '操作失败，请稍后重试',
        sessionExpired: '登录已失效，请重新登录',
        forbidden: '没有产品线管理权限',
        conflict: '产品线 App Key 已存在，请换一个产品线代码',
        invalid: '产品线信息不完整，请检查名称、代码、状态和默认入口'
      },
      list: {
        keywordAria: '产品线关键字',
        keywordPlaceholder: '产品线 / App Key / 说明',
        statusAria: '产品线状态',
        allStatuses: '全部状态',
        create: '新增产品线',
        columns: {
          productLine: '产品线',
          status: '状态',
          defaultEntry: '默认入口',
          sortOrder: '排序',
          actions: '操作'
        },
        empty: '暂无产品线',
        unconfigured: '未配置',
        edit: '编辑'
      },
      drawer: {
        closeAria: '关闭产品线编辑',
        createLabel: '新增产品线',
        editLabel: '产品线',
        createTitle: '登记产品线',
        close: '关闭',
        settings: '产品线设置',
        productCode: '产品线代码',
        productCodePlaceholder: '例如 crm suite',
        appKeyHint: '请输入英文开头的产品线代码，系统会生成 snake_case App Key。',
        displayName: '展示名称',
        status: '状态',
        defaultEntry: '默认入口',
        sortOrder: '排序',
        description: '说明',
        createNote: '新增产品线默认建议先停用，后续业务开发完成后再启用给租户选择。',
        editNote: '停用产品线会影响所有已购买该产品线的租户。',
        createAction: '创建产品线',
        saveAction: '保存产品线'
      },
      priceForm: {
        title: '定价',
        monthlyAmount: '月付价格',
        yearlyAmount: '年付价格',
        currency: '币种',
        monthlyStatus: '月付状态',
        yearlyStatus: '年付状态',
        save: '保存定价'
      },
      entryRoutes: {
        none: {
          label: '暂不配置入口',
          description: '先登记产品线，后续开发完成后再配置默认入口'
        },
        staffHome: {
          label: '门店员工首页',
          description: '预约排队叫号系统默认入口'
        }
      },
      status: {
        active: '启用',
        disabled: '停用'
      }
    },
    tenants: {
      status: {
        created: '已创建',
        active: '启用',
        suspended: '停用',
        closed: '关闭',
        deleted: '已删除'
      },
      errors: {
        operationFailed: '操作失败',
        sessionExpired: '登录已失效',
        forbidden: '没有平台后台权限',
        conflict: '租户代码或管理员账号已存在',
        notFound: '租户不存在',
        invalid: '请检查必填项和 6 位密码',
        operatingEntityHasStores: '该经营主体仍有门店，无法删除'
      },
      list: {
        kickerPlatform: '平台',
        kickerBilling: '计费',
        title: '集团 / 租户管理',
        billingTitle: '租户计费',
        keywordPlaceholder: '代码 / 名称 / 电话 / 地址',
        statusFilterAria: '租户状态筛选',
        allFilter: '全部 {count}',
        activeFilter: '正常 {count}',
        deletedFilter: '已删除 {count}',
        createGroup: '新增集团',
        createSingle: '新增单店',
        confirmDelete: '删除租户 {tenantCode}？'
      },
      table: {
        columns: {
          tenantCode: '租户代码',
          name: '名称',
          principal: '负责人',
          phone: '电话',
          address: '地址',
          status: '状态',
          updatedAt: '更新时间',
          actions: '操作'
        },
        empty: '暂无租户',
        structure: '门店结构',
        billingShort: '计费',
        billingFull: '订阅/计费'
      },
      formPage: {
        createGroupTitle: '新增集团',
        createSingleTitle: '新增单店',
        editTitle: '编辑租户',
        kicker: '平台',
        backToList: '返回列表'
      },
      form: {
        basicInfo: '基础信息',
        contactInfo: '联系方式',
        adminAccount: '租户管理员账号',
        tenantLogo: '租户 LOGO',
        tenantCode: '租户代码',
        name: '名称',
        status: '状态',
        defaultLocale: '默认语言',
        principal: '负责人',
        phone: '电话',
        address: '地址',
        initialPassword: '租户管理员初始密码',
        groupInitialPassword: '集团管理员初始密码',
        password: '修改密码',
        initialPasswordPlaceholder: '6 位数字或英文字母',
        passwordPlaceholder: '留空则不修改',
        chooseImage: '选择图片',
        uploadLogo: '上传 LOGO',
        clearLogo: '清空 LOGO',
        onboardingMode: {
          title: '开户模式',
          singleStore: '单店快速开户',
          singleStoreHint: '自动创建默认经营主体和默认门店',
          groupMultiStore: '集团多店开户',
          groupMultiStoreHint: '先创建管理租户，再配置经营主体和分店'
        },
        adminStoreAccess: {
          title: '授权门店',
          defaultStore: '默认门店',
          empty: '暂无可授权门店'
        }
      },
      structure: {
        kicker: '租户结构',
        title: '经营主体与门店',
        summary: {
          aria: '经营主体与门店概览',
          operatingEntities: '经营主体',
          stores: '门店',
          activeStores: '启用门店',
          selectedStores: '当前主体门店'
        },
        tabs: {
          aria: '经营主体与门店切换'
        },
        guide: {
          noEntities: '暂无经营主体，请先新增经营主体。',
          noStores: '默认经营主体已就绪，可以继续新增分店。',
          noActiveEntities: '需要先启用默认经营主体，才能新增分店。',
          noStoresForEntity: '当前经营主体还没有分店。'
        },
        actions: {
          newEntity: '新增经营主体',
          newStore: '新增分店',
          deleteStore: '删除',
          deleteEntity: '删除',
          confirmDeleteStore: '删除门店 {storeName}？删除后该门店登录、授权、子域名入口和活跃计费项将停用。',
          confirmDeleteEntity: '删除经营主体 {entityName}？历史记录将保留，删除后不可在当前列表恢复。'
        },
        formTitles: {
          editEntity: '编辑经营主体',
          editStore: '编辑门店'
        },
        operatingEntities: {
          title: '经营主体',
          empty: '暂无经营主体',
          hint: '按经营主体管理门店归属、联系人和地址'
        },
        stores: {
          title: '门店',
          empty: '暂无门店',
          hint: '门店代码会用于登录子域名和门店路由'
        },
        adminStoreAccess: {
          title: '授权门店',
          defaultStore: '默认门店',
          empty: '当前经营主体暂无可授权门店。',
          save: '保存授权',
          defaultStoreElsewhere: '默认门店在其他经营主体。'
        },
        fields: {
          entityCode: '经营主体代码',
          displayName: '名称',
          status: '状态',
          defaultLocale: '默认语言',
          principal: '负责人',
          phone: '电话',
          address: '地址',
          operatingEntity: '经营主体',
          chooseOperatingEntity: '请选择经营主体',
          storeCode: '门店代码',
          storeName: '门店名称',
          locale: '语言',
          timezone: '时区',
          currency: '币种',
          dateFormat: '日期格式',
          timeFormat: '时间格式',
          supplementalInfo: '补充资料',
          operationDefaults: '运营默认值',
          branchAdminAccount: '分店管理员账号',
          branchAdminUsername: '分店管理员登录账号',
          branchAdminPassword: '分店管理员密码',
          unassigned: '未分配经营主体'
        },
        status: {
          created: '已创建',
          active: '启用',
          inactive: '停用',
          archived: '已归档'
        }
      }
    },
    billing: {
      page: {
        kicker: '租户管理',
        title: '订阅 / 计费'
      },
      messages: {
        saved: '已保存'
      },
      errors: {
        operationFailed: '操作失败',
        sessionExpired: '登录已失效',
        forbidden: '没有计费管理权限',
        subscriptionConflict: '订阅状态冲突',
        versionConflict: '订阅已被其他操作更新，请刷新后重试',
        legacyConvertCycle: '历史赠送只能转月付或年付'
      },
      notes: {
        manualSuspend: '手工暂停',
        manualCancel: '手工取消'
      },
      actions: {
        purchase: '购买',
        open: '开通',
        convert: '转付费',
        reactivate: '重新开通',
        resumeRenew: '恢复并续费',
        renewStore: '续费当前门店',
        renew: '续费',
        suspend: '暂停',
        cancel: '取消'
      },
      cycles: {
        legacyGrant: '历史赠送 / 永久有效',
        monthly: '月付',
        yearly: '年付',
        manual: '手工'
      },
      status: {
        notOpened: '未开通',
        expired: '已到期',
        active: '生效中',
        suspended: '已暂停',
        cancelled: '已取消',
        permanent: '永久有效'
      },
      units: {
        year: '年',
        month: '个月'
      },
      table: {
        columns: {
          productLine: '产品线',
          billingCycle: '计费周期',
          status: '状态',
          periodStart: '有效期开始',
          periodEnd: '有效期结束',
          amount: '金额',
          currency: '币种',
          entitlement: '授权',
          actions: '操作'
        },
        empty: '暂无产品线'
      },
      form: {
        title: '手工购买 / 续费',
        productLine: '产品线',
        billingCycle: '计费周期',
        duration: '购买数量',
        unitPrice: '标准单价',
        storeCount: '计费门店数',
        storeUnitAmount: '单店金额',
        amount: '本次金额',
        currency: '币种',
        paymentNote: '备注',
        noBillableStores: '请先在租户结构中创建并启用门店，再开通或续费产品。'
      },
      storeItems: {
        title: '门店计费明细',
        empty: '暂无门店计费明细'
      }
    },
    profile: {
      page: {
        kicker: '平台',
        title: '平台资料'
      },
      errors: {
        operationFailed: '操作失败',
        sessionExpired: '登录已失效',
        forbidden: '没有平台后台权限',
        invalid: '请检查必填项'
      },
      confirmDeleteSocial: '删除社交媒体 {name}？',
      fields: {
        platformProfile: '平台资料',
        platformName: '平台名称',
        address: '地址',
        phone: '电话',
        email: '电邮',
        website: '网址',
        platformLogo: '平台 LOGO',
        socialMedia: '社交媒体',
        socialLogo: '社媒 LOGO',
        name: '名称',
        sortOrder: '排序',
        status: '状态',
        chooseLogo: '选择 LOGO'
      },
      social: {
        createLogoAria: '新增社媒 LOGO',
        nameAria: '社交媒体名称',
        urlAria: '社交媒体 URL',
        empty: '暂无社交媒体',
        logoAlt: '{name} 社媒 LOGO',
        uploadLogo: '上传 LOGO'
      }
    }
  },
  tenant: {
    i18nCatalog: {
      page: {
        kicker: '租户配置',
        title: '国际化字典',
        note: '这里只维护平台授权的业务文案覆盖值。留空并保存会清除当前覆盖，按门店覆盖、租户覆盖、平台默认、前端兜底的顺序显示。'
      },
      scope: {
        store: '门店覆盖',
        tenant: '租户覆盖'
      },
      sources: {
        store: '门店覆盖',
        tenant: '租户覆盖',
        platform: '平台默认',
        frontend: '前端兜底'
      },
      fields: {
        namespaceFilter: '字典命名空间筛选',
        allNamespaces: '全部命名空间',
        scopeLevel: '覆盖层级',
        override: '覆盖文案',
        effective: '当前生效',
        clearOverride: '清除覆盖',
        noPlaceholders: '无占位符',
        empty: '暂无匹配字典'
      },
      messages: {
        saved: '覆盖文案已保存',
        noChanges: '没有需要保存的覆盖文案'
      },
      errors: {
        operationFailed: '操作失败，请稍后重试',
        sessionExpired: '登录已失效，请重新登录',
        forbidden: '没有租户后台权限',
        invalid: '请检查字典内容和语言',
        versionConflict: '字典已被其他操作更新，请刷新后重试',
        placeholderUnknown: '模板包含未授权占位符',
        keyNotAllowed: '该文案不允许租户维护'
      }
    },
    staffList: {
      storeAccess: {
        authorized: '授权门店',
        defaultStore: '默认门店'
      }
    },
    staffForm: {
      storeAccess: {
        title: '授权门店',
        defaultStore: '默认门店',
        empty: '暂无可授权门店，请先确认当前账号已获得门店授权。'
      },
      errors: {
        storeRequired: '请至少选择一个授权门店',
        defaultStoreRequired: '默认门店必须在授权门店内'
      }
    }
  },
  staffHome: {
    appStatus: {
      refreshing: '刷新中',
      unavailable: '暂不可用',
      home: '首页',
      available: '应用可用'
    },
    topbar: {
      aria: '员工工作台顶部栏',
      metaAria: '门店和应用状态',
      fallbackTitle: '门店管理',
      kicker: '门店员工',
      logoAlt: '{name} 品牌标志'
    },
    actions: {
      checkIn: {
        label: '预约到店',
        description: '从今日预约确认客人到店',
        symbol: '到'
      },
      callQueue: {
        label: '排队叫号',
        description: '从列表选择排队票一键叫号',
        symbol: '叫'
      },
      seatQueue: {
        label: '排队入座',
        description: '从已叫号票直接安排桌台',
        symbol: '座'
      }
    },
    store: {
      defaultLabel: '默认门店',
      label: '门店 {shortId}'
    },
    date: {
      today: '今日'
    },
    aria: {
      businessDate: '今日营业日期',
      overviewLoadFailed: '今日概览加载失败',
      operations: '门店员工操作',
      unavailableByPermission: '当前权限无可用入口',
      todayOverview: '今日概览',
      queuePartyGroups: '当前排队人数组',
      tableStatus: '桌台状态'
    },
    empty: {
      noEntry: '当前权限无可用入口',
      permissionHint: '入口会按 App Gate 权限自动显示。'
    },
    hints: {
      unavailable: '今日概览暂不可用',
      loading: '正在读取今日营业数据',
      queuePressure: '排队 {groups} 组，优先看等待和已叫号',
      calm: '当前没有排队压力'
    },
    errors: {
      overviewUnavailable: '今日概览暂不可用',
      overviewLoadFailed: '暂时无法读取今日概览，请稍后重试。'
    },
    units: {
      groups: '组',
      tables: '张',
      people: '{count} 人',
      totalTables: '共 {count} 张',
      queueSummary: '{groups} 组 / {people} 人',
      partySizeSummary: '{groups}组 / {people}人',
      tableSummary: '{available} 可用 / {total} 总桌'
    },
    kpis: {
      reservations: '今日预约',
      arrived: '已到店',
      queue: '当前排队',
      tables: '可用桌台'
    },
    queueRows: {
      waiting: {
        label: '等待中'
      },
      called: {
        label: '已叫号'
      },
      skipped: {
        label: '已过号',
        detail: '可重回或取消'
      }
    },
    tableRows: {
      available: {
        label: '可用',
        detail: '可安排入座'
      },
      occupied: {
        label: '占用',
        detail: '当前服务中'
      },
      reserved: {
        label: '预留',
        detail: '预约占用'
      },
      cleaning: {
        label: '清台',
        detail: '待恢复可用'
      },
      temporary: {
        label: '临时组',
        detail: '按桌组使用'
      }
    }
  },
  staffControls: {
    businessDate: {
      aria: '业务日期',
      calendarLabel: '业务日期日历',
      today: '今日 {date}',
      future: '未来日期 {date}',
      past: '过去日期 {date}',
      todayMode: '营业中',
      futureMode: '规划模式',
      pastMode: '历史查看',
      changeDate: '切换日期',
      editDate: '改日期',
      backToday: '回到今日'
    },
    guest: {
      nameLabel: '顾客姓名',
      namePlaceholder: '姓名',
      salutationAria: '顾客称呼',
      phoneLabel: '手机号',
      salutations: {
        mr: '先生',
        ms: '女士'
      },
      lookup: {
        found: '已识别顾客',
        notFound: '新手机号',
        error: '顾客识别失败',
        lookingUp: '识别中...'
      }
    },
    timePicker: {
      aria: '24小时制时间选择',
      hour: '时',
      minute: '分',
      done: '完成'
    },
    workflow: {
      aria: '门店流转提示',
      walkInSeating: '散客入座',
      occupied: '占用',
      cleaning: '清台',
      available: '可用'
    },
    tablePicker: {
      aria: '桌号及分组选取',
      title: '桌号及分组',
      selectionModeAria: '资源选择模式',
      singleMode: '单桌/桌组',
      temporaryMode: '临时组合',
      loading: '正在读取桌台资源',
      areaFilterAria: '桌台分区',
      areaFilter: '桌台分区',
      allAreas: '全部分区',
      tableGroupAria: '{area}桌号',
      groupAria: '桌组',
      groups: '分组',
      capacityRange: '{min}-{max}人',
      requiredResource: '预约指定',
      mustUseRequiredResource: '需使用预约指定桌台',
      unassignedArea: '未分区',
      subtitle: {
        temporary: '临时组合 · 已选 {count} 张',
        configured: '后台已配置资源'
      },
      empty: {
        currentAreaNoAvailable: '当前分区暂无可用桌台。',
        currentAreaNoTables: '当前分区暂无桌台。',
        noAvailable: '暂无可用桌台。',
        noTables: '暂无桌台，请先在后台配置桌台。'
      },
      status: {
        available: '可用',
        occupied: '占用',
        cleaning: '清台中',
        locked: '锁定',
        reserved: '预留',
        inactive: '停用',
        active: '分组',
        created: '已创建',
        released: '已释放',
        ended: '已结束'
      },
      unavailable: {
        default: '当前不可选',
        statusUnavailable: '当前状态不可选',
        capacityMismatch: '人数不匹配',
        locked: '桌台已锁定',
        occupied: '桌台已占用',
        cleaning: '正在清台',
        reservationPreassigned: '已被预约预留',
        temporaryGroupMember: '临时组占用',
        summary: '{status}，{reason}'
      }
    }
  }
}
