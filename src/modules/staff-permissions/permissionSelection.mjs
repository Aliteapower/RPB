/** Select operations with their prerequisites; removing a prerequisite revokes dependent operations. */
export function togglePermission(selected, code, checked, choices) {
  const byCode = new Map(choices.map(choice => [choice.code, choice]))
  const next = new Set(selected)
  if (checked) {
    const additions = new Set()
    function add(key) {
      if (next.has(key) || additions.has(key)) return true
      const choice = byCode.get(key)
      if (!choice?.grantable) return false
      additions.add(key)
      return (choice.requires ?? []).every(add)
    }
    if (add(code)) additions.forEach(key => next.add(key))
  } else {
    next.delete(code)
    let changed = true
    while (changed) {
      changed = false
      for (const key of next) {
        if ((byCode.get(key)?.requires ?? []).some(required => !next.has(required))) {
          next.delete(key)
          changed = true
        }
      }
    }
  }
  return [...next].sort()
}

export function permissionsForStore(base, storePermissions, storeId) {
  return [...(storeId && Object.hasOwn(storePermissions ?? {}, storeId) ? storePermissions[storeId] : base)]
}
