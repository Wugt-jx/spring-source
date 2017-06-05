package com.wgt.core.core;

import com.wgt.core.util.Assert;
import com.wgt.core.util.StringValueResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wgt on 2017/6/5.
 */
public class SimpleAliasRegistry implements AliasRegistry {

    private Map<String,String> aliasMap = new ConcurrentHashMap<String, String>();


    /**
     * 注册别名
     * @param name the canonical name
     * @param alias the alias to be registered
     */
    public void registerAlias(String name, String alias) {
        //参数校验
        Assert.hasText(name, "'name' must not be empty");
        Assert.hasText(alias, "'alias' must not be empty");
        if (alias.equals(name)) {
            this.aliasMap.remove(alias);
        }else {
            String registeredName = this.aliasMap.get(alias);
            if (registeredName != null) {
                if (registeredName.equals(name)) {
                    // An existing alias - no need to re-register
                    return;
                }
                if (!allowAliasOverriding()) {
                    throw new IllegalStateException("Cannot register alias '" + alias + "' for name '" +
                            name + "': It is already registered for name '" + registeredName + "'.");
                }
            }
            checkForAliasCircle(name, alias);
            this.aliasMap.put(alias, name);
        }
    }

    /**
     * 删除别名
     * @param alias the alias to remove
     */
    public void removeAlias(String alias) {
        String name = this.aliasMap.remove(alias);
        if (name == null) {
            throw new IllegalStateException("No alias '" + alias + "' registered");
        }
    }

    /**
     * 判断name是否为别名
     * @param name the name to check
     * @return
     */
    public boolean isAlias(String name) {
        return false;
    }

    /**
     * 返回该name注册的所有的别名
     * @param name
     * @return
     */
    public String[] getAliases(String name) {
        return new String[0];
    }


    /**
     * 是否允许别名被覆盖
     * @return
     */
    protected boolean allowAliasOverriding() {
        return true;
    }

    /**
     * 检查name的该别名是否存在
     * @param name
     * @param alias
     */
    protected void checkForAliasCircle(String name, String alias) {
        if (hasAlias(alias, name)) {
            throw new IllegalStateException("Cannot register alias '" + alias +
                    "' for name '" + name + "': Circular reference - '" +
                    name + "' is a direct or indirect alias for '" + alias + "' already");
        }
    }

    /**
     * 判断该name的bean是否有此别名
     * @param name
     * @param alias
     * @return
     */
    public boolean hasAlias(String name, String alias) {
        for (Map.Entry<String, String> entry : this.aliasMap.entrySet()) {
            String registeredName = entry.getValue();
            if (registeredName.equals(name)) {
                String registeredAlias = entry.getKey();
                return (registeredAlias.equals(alias) || hasAlias(registeredAlias, alias));
            }
        }
        return false;
    }

    /**
     * 返回该name注册的所有的别名，对list进行添加元素
     * @param name
     * @return
     */
    private void retrieveAliases(String name, List<String> result) {
        for (Map.Entry<String, String> entry : this.aliasMap.entrySet()) {
            String registeredName = entry.getValue();
            if (registeredName.equals(name)) {
                String alias = entry.getKey();
                result.add(alias);


                 //这里递归调用，需要清楚HashMap是一个链表集合的数据结构
                retrieveAliases(alias, result);
            }
        }
    }

    /**
     * 将valueResolver所带的alias和name写入缓存
     * @param valueResolver
     */
    public void resolveAliases(StringValueResolver valueResolver) {
        Assert.notNull(valueResolver, "StringValueResolver must not be null");

        //疑问：aliasMap是concurrentMap，线程安全，为什么还要用synchronize进行同步？
        synchronized (this.aliasMap) {
            Map<String, String> aliasCopy = new HashMap<String, String>(this.aliasMap);
            for (String alias : aliasCopy.keySet()) {
                String registeredName = aliasCopy.get(alias);
                String resolvedAlias = valueResolver.resolveStringValue(alias);
                String resolvedName = valueResolver.resolveStringValue(registeredName);
                if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
                    this.aliasMap.remove(alias);
                }
                else if (!resolvedAlias.equals(alias)) {
                    String existingName = this.aliasMap.get(resolvedAlias);
                    if (existingName != null) {
                        if (existingName.equals(resolvedName)) {
                            // Pointing to existing alias - just remove placeholder
                            this.aliasMap.remove(alias);
                            break;
                        }
                        throw new IllegalStateException(
                                "Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
                                        "') for name '" + resolvedName + "': It is already registered for name '" +
                                        registeredName + "'.");
                    }
                    checkForAliasCircle(resolvedName, resolvedAlias);
                    this.aliasMap.remove(alias);
                    this.aliasMap.put(resolvedAlias, resolvedName);
                }
                else if (!registeredName.equals(resolvedName)) {
                    this.aliasMap.put(alias, resolvedName);
                }
            }
        }
    }


    public String canonicalName(String name) {
        String canonicalName = name;
        // Handle aliasing...
        String resolvedName;
        do {
            resolvedName = this.aliasMap.get(canonicalName);
            if (resolvedName != null) {
                canonicalName = resolvedName;
            }
        }
        while (resolvedName != null);
        return canonicalName;
    }
}
