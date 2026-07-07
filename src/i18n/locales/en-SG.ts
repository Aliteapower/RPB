export const enSG = {
  common: {
    actions: {
      login: 'Sign in',
      loggingIn: 'Signing in...',
      logout: 'Sign out',
      loggingOut: 'Signing out',
      refresh: 'Refresh',
      loading: 'Loading',
      query: 'Search',
      reset: 'Reset',
      previousPage: 'Previous',
      nextPage: 'Next',
      saving: 'Saving',
      save: 'Save',
      edit: 'Edit',
      upload: 'Upload',
      clear: 'Clear',
      delete: 'Delete',
      restore: 'Restore',
      add: 'Add',
      close: 'Close',
      cancel: 'Cancel'
    },
    dateTime: {
      date: 'Date',
      time: 'Time',
      year: 'Year',
      month: 'Month',
      day: 'Day',
      hour: 'Hour',
      minute: 'Minute'
    },
    pagination: {
      summary: 'Rows {start}-{end} / {total}'
    },
    password: {
      show: 'Show password',
      hide: 'Hide password'
    },
    localeSwitcher: {
      aria: 'Language switcher',
      zhCN: '中文',
      enSG: 'EN'
    },
    product: {
      reservationQueueCallSystem: 'Reservation, queue, and calling system'
    }
  },
  i18nCatalog: {
    namespaces: {
      reason: 'Reason copy',
      status: 'Status copy',
      public_booking: 'Public booking',
      reservation_share: 'Reservation share',
      queue: 'Queue',
      call_screen: 'Call screen',
      reservation_meal_period: 'Reservation meal periods'
    },
    categories: {
      cancellation: 'Cancellation',
      no_show: 'No-show',
      queue: 'Queue',
      table: 'Tables',
      cleaning: 'Cleaning',
      reservation: 'Reservations',
      prompt: 'Prompt copy',
      template: 'Template',
      display: 'Display copy',
      display_name: 'Display name',
      restaurant_default: 'Restaurant default'
    },
    textKinds: {
      label: 'Label',
      template: 'Template',
      status: 'Status',
      prompt: 'Prompt copy'
    }
  },
  components: {
    callScreenAdModeSwitch: {
      text: 'Text carousel',
      media: 'Image/video carousel',
      aria: 'Carousel type'
    },
    downloadableQrCode: {
      title: 'QR code',
      aria: 'QR code download',
      download: 'Download QR code',
      rendering: 'Generating',
      errors: {
        empty: 'QR code content is empty',
        renderFailed: 'QR code generation failed'
      }
    }
  },
  nav: {
    platform: {
      aria: 'Platform admin navigation',
      title: 'Platform admin',
      tenants: 'Tenants',
      billing: 'Tenant billing',
      profile: 'Platform profile',
      productLines: 'Product lines',
      i18nCatalog: 'I18n catalog',
      callScreenSeed: 'Call screen templates',
      mealPeriodSeed: 'Reservation meal periods',
      shareTemplateSeed: 'Confirmation templates'
    },
    tenant: {
      aria: 'Tenant admin navigation',
      title: 'Tenant admin',
      storePrefix: 'Store',
      profile: 'Tenant profile',
      staff: 'Staff',
      customers: 'Customers',
      tables: 'Tables',
      settings: 'Settings',
      i18nCatalog: 'I18n catalog',
      shareTemplate: 'Booking share',
      publicBooking: 'Public booking',
      callScreen: 'Call screen'
    },
    staff: {
      aria: 'Staff workbench navigation',
      home: 'Home',
      reservation: 'Bookings',
      queue: 'Queue',
      table: 'Tables'
    }
  },
  login: {
    shellAria: 'Back office sign in',
    heading: 'Back office entry',
    entryTabAria: 'Choose back office entry',
    passwordPolicy: 'Password must be 6 digits or letters.',
    fields: {
      tenantCode: 'Tenant code',
      employeeUsername: 'Staff account',
      password: 'Password'
    },
    remember: {
      account: 'Remember account'
    },
    captcha: {
      aria: 'Slider captcha',
      refresh: 'New image',
      loading: 'Loading'
    },
    entries: {
      platformAdmin: {
        tab: 'Platform',
        title: 'Platform admin',
        description: 'Create tenants, enable tenant admin, and manage platform settings.',
        accountLabel: 'Platform account',
        targetHint: 'Platform scope'
      },
      tenantAdmin: {
        tab: 'Tenant',
        title: 'Tenant admin',
        description: 'Configure stores, staff accounts, and permission scope.',
        accountLabel: 'Tenant account',
        targetHint: 'Tenant {tenantCode}'
      },
      tenantStaff: {
        tab: 'Staff',
        title: 'Tenant staff',
        description: 'Enter authorised stores. Tenant subdomains no longer require typing the tenant code.',
        accountLabel: 'Staff account',
        targetHint: 'Tenant {tenantCode} / Staff {employeeUsername}'
      }
    },
    store: {
      authorized: 'Authorised stores',
      authorizedDescription: 'After sign-in, you enter by store access and can switch when multiple stores are granted.',
      selectionAria: 'Choose authorised store',
      select: 'Choose store',
      enter: 'Enter store',
      switch: 'Switch store'
    },
    errors: {
      captchaLoadFailed: 'Slider captcha failed to load.',
      missingStoreScope: 'This account is not linked to a store. Ask a platform admin to finish tenant setup.',
      loginFailed: 'Sign in failed.',
      captchaMismatch: 'Slider captcha did not pass.',
      invalidCredentials: 'Account or password is incorrect.'
    }
  },
  appGate: {
    errors: {
      tenantAppNotEnabled: {
        title: 'Reservation queue product is not enabled',
        message: 'Ask a platform admin to enable the product line in tenant billing before using it.'
      },
      tenantAppExpired: {
        title: 'Product subscription has expired',
        message: 'Ask a platform admin to renew the reservation queue product before using it.'
      },
      storeAppNotEnabled: {
        title: 'Current store is not enabled',
        message: 'Ask an admin to check this store product configuration.'
      },
      permissionDenied: {
        title: 'This account does not have permission',
        message: 'Ask an admin to adjust staff permissions before using this feature.'
      },
      appDisabled: {
        title: 'Product line is unavailable',
        message: 'Ask a platform admin to check the product line status.'
      },
      loadFailed: {
        title: 'Load failed',
        message: 'Please try again later.'
      }
    }
  },
  storeSwitcher: {
    label: 'Switch store',
    unknown: 'Current store'
  },
  reservationCreate: {
    errors: {
      startInPast: 'Reservation start time cannot be earlier than now. Choose a later time.',
      invalidPartySize: 'Party size must be greater than 0.',
      invalidTimeRange: 'Reservation time is invalid. Choose a time again.',
      timeSlotUnavailable: 'Choose an available time within the store meal period.',
      invalidPhoneE164: 'Mobile number must be an 8-digit Singapore number.',
      requestFailed: 'Reservation creation failed. Please try again later.',
      networkFailure: 'Network request failed. Check the connection and try again.',
      forbidden: 'This account cannot create reservations.',
      customerNotFound: 'Customer was not found. Check the phone number or enter the details again.',
      duplicateActive: 'This customer already has an active reservation.',
      capacityInsufficient: 'Capacity is insufficient for this time slot. Adjust the time or party size.'
    }
  },
  reservationPublicShare: {
    errors: {
      expired: 'This link has expired',
      notFound: 'Reservation information was not found',
      loadFailed: 'Reservation information could not be loaded'
    }
  },
  reservationShareTemplatePreview: {
    variables: {
      storeName: 'Sample store',
      reservationNo: 'R-EXAMPLE-0001',
      reservationCode: 'R-EXAMPLE-0001',
      reservationDate: '15-07-2026 (Wednesday)',
      reservationTime: '19:30',
      reservedStartAt: '15-07-2026 (Wednesday) 19:30',
      partySize: '2',
      tableCode: 'A01',
      holdMinutes: '15',
      contactName: 'Sample guest',
      guestSalutation: 'Mr',
      maskedPhone: '0000****',
      storeAddress: '1 Sample Address',
      googleMapUrl: 'https://example.com/map',
      storePhone: '0000 0000',
      arrivalNote: 'Please arrive 10 minutes early',
      confirmInstruction: 'Reply to confirm and keep the booking',
      cancelInstruction: 'Contact us 2 hours ahead if you need to cancel',
      changeInstruction: 'Call the store if you need to change party size or time',
      replyInstruction: 'Reply to confirm when received'
    }
  },
  reservationWorkbench: {
    statuses: {
      confirmed: 'Booked',
      arrived: 'Arrived',
      seated: 'Seated',
      cancelled: 'Cancelled',
      noShow: 'No-show',
      completed: 'Completed',
      draft: 'Draft'
    },
    queueStatuses: {
      waiting: 'Waiting',
      called: 'Called',
      skipped: 'Skipped',
      rejoined: 'Rejoined',
      seated: 'Seated',
      cancelled: 'Queue cancelled',
      expired: 'Queue expired',
      queued: 'Queued'
    },
    share: {
      prepared: 'Link ready',
      aria: 'Reservation link forwarding',
      loading: 'Loading',
      whatsapp: 'Send WhatsApp',
      wechat: 'Send WeChat',
      system: 'System share',
      copy: 'Copy link',
      linkAria: 'Reservation share link',
      noForwardLink: 'No forwarding link available',
      copied: 'Link copied',
      nativeOpened: 'System share opened',
      systemFallbackCopied: 'System share unavailable. Link copied.',
      manualShare: 'Browser blocked sharing. Copy the link below manually.',
      phoneUnavailable: 'Guest mobile number is not available for WhatsApp',
      phoneMissing: 'Guest did not provide an available mobile number',
      whatsappOpening: 'Opening WhatsApp as {sender}',
      defaultSender: 'Store',
      noText: 'No share text available',
      manualTextCopy: 'Browser blocked copying. Copy the text manually and open WeChat.',
      wechatOpening: 'Text copied. Opening WeChat.',
      noCopyLink: 'No link available to copy',
      manualCopyLink: 'Browser blocked copying. Copy the link below manually.',
      loadFailed: 'Reservation share information failed to load'
    },
    todayList: {
      aria: 'Today reservations',
      title: 'Today reservations',
      total: '{count} total',
      statusFilterAria: 'Status filter',
      filtersAria: 'Reservation filters',
      phone: 'Mobile number',
      partySize: 'Party size',
      allPartySizes: 'All party sizes',
      partySizeOption: '{size} pax',
      loadingTitle: 'Loading...',
      loadingDescription: 'Reading reservations for the current store.',
      loadFailedTitle: 'Load failed',
      loadFailedMessage: 'Unable to read today reservations. Please try again later.',
      cancelFailedTitle: 'Cancellation failed',
      cancelFailedMessage: 'Unable to cancel the reservation. Please try again later.',
      statusActionFailedTitle: 'Status update failed',
      statusActionFailedMessage: 'Unable to update reservation status. Please try again later.',
      emptyTitle: 'No reservations today',
      emptyDescription: 'Try another date or status filter.',
      filteredEmptyTitle: 'No matching reservations',
      filteredEmptyDescription: 'Reset filters and try again.',
      itemsAria: 'Today reservation list'
    },
    item: {
      currentDayOnly: 'Only same-day reservations can be operated',
      seatFromQueue: 'Seat queue',
      seatDirect: 'Seat',
      jumping: 'Opening',
      seating: 'Seating',
      completed: 'Completed',
      seated: 'Seated',
      reservationAssigned: 'Reservation assigned',
      tableAssigned: '{resource}: {code} ({label})',
      tableUnassigned: 'Table: unassigned',
      tableSeated: 'Table: seated',
      unset: 'Not filled',
      queueTicket: 'Queue ticket',
      tableGroup: 'Table group',
      diningTable: 'Table',
      tableResource: 'Table resource',
      partySizeSummary: '{count} pax · {table}',
      actionsAria: 'Reservation actions',
      checkIn: 'Arrive',
      checkingIn: 'Arriving',
      noShow: 'No-show',
      noShowing: 'Marking',
      cancelTitle: 'Cancel reservation',
      cancelling: 'Cancelling'
    },
    quickActions: {
      aria: 'Reservation management',
      title: 'Reservation management',
      createReservation: 'Create reservation',
      createReservationSymbol: 'Res',
      createDisabledTitle: 'Past dates cannot create reservations',
      walkInQueue: 'Walk-in queue',
      walkInQueueSymbol: 'Q',
      reservationToQueue: 'Reservation to queue',
      reservationToQueueSymbol: 'ToQ'
    },
    monthCalendar: {
      aria: 'Reservation calendar',
      monthTitle: '{month}/{year}',
      selectedPrefix: 'Selected',
      selectPrefix: 'Select',
      reservationCount: ', {count} reservations',
      noReservation: ', no reservations',
      dateLimit: ', cannot create new reservations',
      previousMonth: 'Previous month',
      nextMonth: 'Next month',
      weekdays: {
        sunday: 'Sun',
        monday: 'Mon',
        tuesday: 'Tue',
        wednesday: 'Wed',
        thursday: 'Thu',
        friday: 'Fri',
        saturday: 'Sat'
      }
    },
    dialogs: {
      currentReservation: 'Current reservation',
      unknownTable: 'Current table: not recognised',
      currentTable: 'Current table: {code}',
      seatAria: 'Choose table to seat dialog',
      seatTitle: 'Choose table to seat',
      seatClose: 'Close table selection',
      seatSubject: 'Assign a seat for {customer} (reservation)',
      switchAria: 'Choose table switch dialog',
      switchTitle: 'Choose table (switch)',
      switchClose: 'Close table switch',
      switchSubject: 'Switch table for {customer}',
      requiredResource: 'Reservation assigned',
      seatFailed: 'Seating failed',
      switchFailed: 'Table switch failed',
      errorCode: 'Error code: {code}',
      messageKey: 'Message key: {messageKey}',
      seatSubmitting: 'Seating...',
      switchSubmitting: 'Switching...',
      confirmSeat: 'Confirm seating',
      confirmSwitch: 'Confirm switch',
      tableGroupWithCode: 'Table group {code}',
      tableWithCode: 'Table {code}',
      unassigned: 'Unassigned'
    },
    createDialog: {
      allMealPeriods: 'All',
      unassigned: 'Unassigned',
      loadingTables: 'Reading tables',
      tableLoadFailed: 'Table list failed to load',
      chooseTable: 'Click to choose table',
      capacity: '{count} pax',
      available: 'Available',
      unavailable: 'Unavailable',
      capacityMismatch: 'Party size mismatch',
      locked: 'Locked',
      occupied: 'Occupied',
      cleaning: 'Cleaning',
      reserved: 'Reserved',
      aria: 'Create reservation dialog',
      title: 'Create reservation',
      closeAria: 'Close create reservation',
      success: 'Reservation created',
      successSummary: '{code} · {count} pax',
      done: 'Done',
      date: 'Date',
      time: 'Time',
      mealFilterAria: 'Meal period filter',
      timeSlotsAria: 'Available reservation times',
      nextDay: ' · next day',
      loadingTime: 'Reading times',
      timeLoadFailed: 'Time list failed to load',
      noTimeSlots: 'No available times',
      partySize: 'Party size',
      customerEmail: 'Email (optional)',
      optionalTable: 'Table (optional)',
      tablePickerAria: 'Choose reservation table dialog',
      tablePickerTitle: 'Choose reservation table',
      closeTablePicker: 'Close table selection',
      currentSelection: 'Current selection: {selection}',
      temporaryGroupAria: 'Reservation temporary group',
      temporaryGroup: 'Temporary group',
      temporarySummary: '{date} · {count} selected',
      groupName: 'Group name',
      groupNamePlaceholder: 'For example Area A temp group 1',
      exitSelection: 'Exit selection',
      composeTables: 'Combine tables',
      saveGroup: 'Save group',
      saveGroupSubmitting: 'Saving',
      saveSubmitting: 'Saving...',
      save: 'Save'
    }
  },
  platform: {
    i18nCatalog: {
      page: {
        kicker: 'Platform settings',
        title: 'I18n catalog',
        note: 'This page maintains platform default business copy only. Login, navigation, buttons, permission errors, and admin menus still ship from frontend locale files.'
      },
      fields: {
        namespaceFilter: 'Catalog namespace filter',
        allNamespaces: 'All namespaces',
        noPlaceholders: 'No placeholders',
        status: 'Status',
        empty: 'No matching catalog entries'
      },
      status: {
        active: 'Enabled',
        inactive: 'Disabled'
      },
      messages: {
        saved: 'Platform defaults saved',
        noChanges: 'No platform copy changes to save'
      },
      errors: {
        operationFailed: 'Operation failed. Please try again later.',
        sessionExpired: 'Session expired. Please sign in again.',
        forbidden: 'You do not have platform i18n catalog permission.',
        invalid: 'Check catalog content, language, and status.',
        versionConflict: 'The catalog was updated elsewhere. Refresh and try again.',
        placeholderUnknown: 'The template contains an unsupported placeholder.',
        keyNotAllowed: 'This message cannot be maintained in the admin console.'
      }
    },
    productLines: {
      page: {
        kicker: 'Settings',
        title: 'Product lines',
        defaultDisplayName: 'Reservation queue product',
        defaultDescription: 'Integrated reservations, queueing, and calling'
      },
      messages: {
        created: 'Product line created',
        saved: 'Saved',
        pricesSaved: 'Prices saved'
      },
      errors: {
        operationFailed: 'Operation failed. Please try again later.',
        sessionExpired: 'Session expired. Please sign in again.',
        forbidden: 'You do not have product line management permission.',
        conflict: 'This product line App Key already exists. Use another product code.',
        invalid: 'Product line information is incomplete. Check name, code, status, and default entry.'
      },
      list: {
        keywordAria: 'Product line keyword',
        keywordPlaceholder: 'Product line / App Key / description',
        statusAria: 'Product line status',
        allStatuses: 'All statuses',
        create: 'Add product line',
        columns: {
          productLine: 'Product line',
          status: 'Status',
          defaultEntry: 'Default entry',
          sortOrder: 'Sort order',
          actions: 'Actions'
        },
        empty: 'No product lines',
        unconfigured: 'Not configured',
        edit: 'Edit'
      },
      drawer: {
        closeAria: 'Close product line editor',
        createLabel: 'Add product line',
        editLabel: 'Product line',
        createTitle: 'Register product line',
        close: 'Close',
        settings: 'Product line settings',
        productCode: 'Product line code',
        productCodePlaceholder: 'For example crm suite',
        appKeyHint: 'Enter a product code that starts with a letter. The system generates a snake_case App Key.',
        displayName: 'Display name',
        status: 'Status',
        defaultEntry: 'Default entry',
        sortOrder: 'Sort order',
        description: 'Description',
        createNote: 'New product lines should usually stay disabled until the business feature is ready for tenants.',
        editNote: 'Disabling a product line affects all tenants that purchased it.',
        createAction: 'Create product line',
        saveAction: 'Save product line'
      },
      priceForm: {
        title: 'Pricing',
        monthlyAmount: 'Monthly price',
        yearlyAmount: 'Yearly price',
        currency: 'Currency',
        monthlyStatus: 'Monthly status',
        yearlyStatus: 'Yearly status',
        save: 'Save pricing'
      },
      entryRoutes: {
        none: {
          label: 'Do not configure entry yet',
          description: 'Register the product line first, then configure a default entry when development is ready.'
        },
        staffHome: {
          label: 'Store staff home',
          description: 'Default entry for the reservation queue product'
        }
      },
      status: {
        active: 'Enabled',
        disabled: 'Disabled'
      }
    },
    tenants: {
      status: {
        created: 'Created',
        active: 'Enabled',
        suspended: 'Disabled',
        closed: 'Closed',
        deleted: 'Deleted'
      },
      errors: {
        operationFailed: 'Operation failed',
        sessionExpired: 'Session expired',
        forbidden: 'You do not have platform admin permission',
        conflict: 'Tenant code or admin account already exists',
        notFound: 'Tenant was not found',
        invalid: 'Check required fields and the 6-character password'
      },
      list: {
        kickerPlatform: 'Platform',
        kickerBilling: 'Billing',
        title: 'Tenant management',
        billingTitle: 'Tenant billing',
        keywordPlaceholder: 'Code / name / phone / address',
        statusFilterAria: 'Tenant status filter',
        allFilter: 'All {count}',
        activeFilter: 'Active {count}',
        deletedFilter: 'Deleted {count}',
        create: 'Add tenant',
        confirmDelete: 'Delete tenant {tenantCode}?'
      },
      table: {
        columns: {
          tenantCode: 'Tenant code',
          name: 'Name',
          principal: 'Principal',
          phone: 'Phone',
          address: 'Address',
          status: 'Status',
          updatedAt: 'Updated at',
          actions: 'Actions'
        },
        empty: 'No tenants',
        billingShort: 'Billing',
        billingFull: 'Subscription/billing'
      },
      formPage: {
        createTitle: 'Add tenant',
        editTitle: 'Edit tenant',
        kicker: 'Platform',
        backToList: 'Back to list'
      },
      form: {
        basicInfo: 'Basic information',
        contactInfo: 'Contact information',
        adminAccount: 'Tenant admin account',
        tenantLogo: 'Tenant logo',
        tenantCode: 'Tenant code',
        name: 'Name',
        status: 'Status',
        defaultLocale: 'Default language',
        principal: 'Principal',
        phone: 'Phone',
        address: 'Address',
        initialPassword: 'Initial password',
        password: 'Change password',
        initialPasswordPlaceholder: '6 digits or letters',
        passwordPlaceholder: 'Leave blank to keep unchanged',
        chooseImage: 'Choose image',
        uploadLogo: 'Upload logo',
        clearLogo: 'Clear logo'
      }
    },
    billing: {
      page: {
        kicker: 'Tenant management',
        title: 'Subscription / billing'
      },
      messages: {
        saved: 'Saved'
      },
      errors: {
        operationFailed: 'Operation failed',
        sessionExpired: 'Session expired',
        forbidden: 'You do not have billing management permission',
        subscriptionConflict: 'Subscription status conflict',
        versionConflict: 'Subscription was updated by another operation. Refresh and try again.',
        legacyConvertCycle: 'Legacy grants can only convert to monthly or yearly billing'
      },
      notes: {
        manualSuspend: 'Manual suspension',
        manualCancel: 'Manual cancellation'
      },
      actions: {
        purchase: 'Purchase',
        open: 'Open',
        convert: 'Convert to paid',
        reactivate: 'Reactivate',
        resumeRenew: 'Resume and renew',
        renew: 'Renew',
        suspend: 'Suspend',
        cancel: 'Cancel'
      },
      cycles: {
        legacyGrant: 'Legacy grant / permanent',
        monthly: 'Monthly',
        yearly: 'Yearly',
        manual: 'Manual'
      },
      status: {
        notOpened: 'Not opened',
        expired: 'Expired',
        active: 'Active',
        suspended: 'Suspended',
        cancelled: 'Cancelled',
        permanent: 'Permanent'
      },
      units: {
        year: 'year',
        month: 'months'
      },
      table: {
        columns: {
          productLine: 'Product line',
          billingCycle: 'Billing cycle',
          status: 'Status',
          periodStart: 'Period start',
          periodEnd: 'Period end',
          amount: 'Amount',
          currency: 'Currency',
          entitlement: 'Entitlement',
          actions: 'Actions'
        },
        empty: 'No product lines'
      },
      form: {
        title: 'Manual purchase / renewal',
        productLine: 'Product line',
        billingCycle: 'Billing cycle',
        duration: 'Quantity',
        unitPrice: 'Standard unit price',
        amount: 'This amount',
        currency: 'Currency',
        paymentNote: 'Note'
      }
    },
    profile: {
      page: {
        kicker: 'Platform',
        title: 'Platform profile'
      },
      errors: {
        operationFailed: 'Operation failed',
        sessionExpired: 'Session expired',
        forbidden: 'You do not have platform admin permission',
        invalid: 'Check required fields'
      },
      confirmDeleteSocial: 'Delete social media {name}?',
      fields: {
        platformProfile: 'Platform profile',
        platformName: 'Platform name',
        address: 'Address',
        phone: 'Phone',
        email: 'Email',
        website: 'Website',
        platformLogo: 'Platform logo',
        socialMedia: 'Social media',
        socialLogo: 'Social logo',
        name: 'Name',
        sortOrder: 'Sort order',
        status: 'Status',
        chooseLogo: 'Choose logo'
      },
      social: {
        createLogoAria: 'New social logo',
        nameAria: 'Social media name',
        urlAria: 'Social media URL',
        empty: 'No social media',
        logoAlt: '{name} social logo',
        uploadLogo: 'Upload logo'
      }
    }
  },
  tenant: {
    i18nCatalog: {
      page: {
        kicker: 'Tenant settings',
        title: 'I18n catalog',
        note: 'This page maintains only platform-authorised business copy overrides. Save a blank override to clear it; display falls back from store override to tenant override, platform default, then frontend fallback.'
      },
      scope: {
        store: 'Store override',
        tenant: 'Tenant override'
      },
      sources: {
        store: 'Store override',
        tenant: 'Tenant override',
        platform: 'Platform default',
        frontend: 'Frontend fallback'
      },
      fields: {
        namespaceFilter: 'Catalog namespace filter',
        allNamespaces: 'All namespaces',
        scopeLevel: 'Override scope',
        override: 'Override copy',
        effective: 'Effective',
        clearOverride: 'Clear override',
        noPlaceholders: 'No placeholders',
        empty: 'No matching catalog entries'
      },
      messages: {
        saved: 'Overrides saved',
        noChanges: 'No overrides to save'
      },
      errors: {
        operationFailed: 'Operation failed. Please try again later.',
        sessionExpired: 'Session expired. Please sign in again.',
        forbidden: 'You do not have tenant admin permission.',
        invalid: 'Check catalog content and language.',
        versionConflict: 'The catalog was updated elsewhere. Refresh and try again.',
        placeholderUnknown: 'The template contains an unsupported placeholder.',
        keyNotAllowed: 'This message cannot be maintained by tenants.'
      }
    },
    staffList: {
      storeAccess: {
        authorized: 'Authorised stores',
        defaultStore: 'Default store'
      }
    },
    staffForm: {
      storeAccess: {
        title: 'Authorised stores',
        defaultStore: 'Default store'
      },
      errors: {
        storeRequired: 'Choose at least one authorised store.',
        defaultStoreRequired: 'Default store must be one of the authorised stores.'
      }
    }
  },
  staffHome: {
    appStatus: {
      refreshing: 'Refreshing',
      unavailable: 'Unavailable',
      home: 'Home',
      available: 'App available'
    },
    topbar: {
      aria: 'Staff workbench top bar',
      metaAria: 'Store and app status',
      brandMark: '食',
      kicker: 'Store staff',
      title: 'Shike Ops'
    },
    actions: {
      checkIn: {
        label: 'Check in booking',
        description: 'Confirm today’s reservation arrival',
        symbol: 'In'
      },
      callQueue: {
        label: 'Call queue',
        description: 'Call a queue ticket from the list',
        symbol: 'Call'
      },
      seatQueue: {
        label: 'Seat queue',
        description: 'Seat a called ticket at a table',
        symbol: 'Seat'
      }
    },
    store: {
      defaultLabel: 'Default store',
      label: 'Store {shortId}'
    },
    date: {
      today: 'Today'
    },
    aria: {
      businessDate: 'Today business date',
      overviewLoadFailed: 'Today overview load failed',
      operations: 'Store staff operations',
      unavailableByPermission: 'No available entry for current permissions',
      todayOverview: 'Today overview',
      queuePartyGroups: 'Current queue party groups',
      tableStatus: 'Table status'
    },
    empty: {
      noEntry: 'No available entry for current permissions',
      permissionHint: 'Entries are shown automatically based on App Gate permissions.'
    },
    hints: {
      unavailable: 'Today overview is unavailable',
      loading: 'Reading today’s operations',
      queuePressure: '{groups} queue groups. Check waiting and called tickets first.',
      calm: 'No queue pressure right now'
    },
    errors: {
      overviewUnavailable: 'Today overview is unavailable',
      overviewLoadFailed: 'Unable to read today overview. Please try again later.'
    },
    units: {
      groups: 'groups',
      tables: 'tables',
      people: '{count} pax',
      totalTables: '{count} tables total',
      queueSummary: '{groups} groups / {people} pax',
      partySizeSummary: '{groups} groups / {people} pax',
      tableSummary: '{available} available / {total} total'
    },
    kpis: {
      reservations: 'Today bookings',
      arrived: 'Arrived',
      queue: 'Current queue',
      tables: 'Available tables'
    },
    queueRows: {
      waiting: {
        label: 'Waiting'
      },
      called: {
        label: 'Called'
      },
      skipped: {
        label: 'Skipped',
        detail: 'Can rejoin or cancel'
      }
    },
    tableRows: {
      available: {
        label: 'Available',
        detail: 'Ready to seat'
      },
      occupied: {
        label: 'Occupied',
        detail: 'Serving now'
      },
      reserved: {
        label: 'Reserved',
        detail: 'Held for bookings'
      },
      cleaning: {
        label: 'Cleaning',
        detail: 'Restore when done'
      },
      temporary: {
        label: 'Temp groups',
        detail: 'Used as table groups'
      }
    }
  },
  staffControls: {
    businessDate: {
      aria: 'Business date',
      calendarLabel: 'Business date calendar',
      today: 'Today {date}',
      future: 'Future date {date}',
      past: 'Past date {date}',
      todayMode: 'Operating',
      futureMode: 'Planning mode',
      pastMode: 'History view',
      changeDate: 'Change date',
      editDate: 'Edit date',
      backToday: 'Back to today'
    },
    guest: {
      nameLabel: 'Guest name',
      namePlaceholder: 'Name',
      salutationAria: 'Guest salutation',
      phoneLabel: 'Mobile number',
      salutations: {
        mr: 'Mr',
        ms: 'Ms'
      },
      lookup: {
        found: 'Customer found',
        notFound: 'New mobile number',
        error: 'Customer lookup failed',
        lookingUp: 'Looking up...'
      }
    },
    timePicker: {
      aria: '24-hour time picker',
      hour: 'Hour',
      minute: 'Minute',
      done: 'Done'
    },
    workflow: {
      aria: 'Store workflow hint',
      walkInSeating: 'Walk-in seating',
      occupied: 'Occupied',
      cleaning: 'Cleaning',
      available: 'Available'
    },
    tablePicker: {
      aria: 'Table and group picker',
      title: 'Tables and groups',
      selectionModeAria: 'Resource selection mode',
      singleMode: 'Single table/group',
      temporaryMode: 'Temporary group',
      loading: 'Reading table resources',
      areaFilterAria: 'Table areas',
      areaFilter: 'Table areas',
      allAreas: 'All areas',
      tableGroupAria: '{area} tables',
      groupAria: 'Table groups',
      groups: 'Groups',
      capacityRange: '{min}-{max} pax',
      requiredResource: 'Assigned resource',
      mustUseRequiredResource: 'Use the assigned table',
      unassignedArea: 'Unassigned',
      subtitle: {
        temporary: 'Temporary group · {count} selected',
        configured: 'Configured back-office resources'
      },
      empty: {
        currentAreaNoAvailable: 'No available tables in this area.',
        currentAreaNoTables: 'No tables in this area.',
        noAvailable: 'No available tables.',
        noTables: 'No tables yet. Configure tables in the back office first.'
      },
      status: {
        available: 'Available',
        occupied: 'Occupied',
        cleaning: 'Cleaning',
        locked: 'Locked',
        reserved: 'Reserved',
        inactive: 'Inactive',
        active: 'Grouped',
        created: 'Created',
        released: 'Released',
        ended: 'Ended'
      },
      unavailable: {
        default: 'Currently unavailable',
        statusUnavailable: 'Current status cannot be selected',
        capacityMismatch: 'Party size mismatch',
        locked: 'Table is locked',
        occupied: 'Table is occupied',
        cleaning: 'Table is being cleaned',
        reservationPreassigned: 'Reserved for a booking',
        temporaryGroupMember: 'Used by temporary group',
        summary: '{status}, {reason}'
      }
    }
  }
}
