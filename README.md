<div align="center">
  <h2>HMA-OSS</h2>

  <img src="HideMyAss-OSS.svg" alt="HMA-OSS Logo" style="max-width:360px;width:60%;height:auto;">

  <p>
    <a href="https://github.com/frknkrc44/HMA-OSS" style="text-decoration:none">
      <img src="https://img.shields.io/github/stars/frknkrc44/HMA-OSS?label=Stars&logo=github">
    </a>
    <a href="https://github.com/frknkrc44/HMA-OSS/actions" style="text-decoration:none">
      <img src="https://img.shields.io/github/actions/workflow/status/frknkrc44/HMA-OSS/main.yml?branch=master&logo=github">
    </a>
    <a href="https://github.com/frknkrc44/HMA-OSS/releases/latest" style="text-decoration:none">
      <img src="https://img.shields.io/github/v/release/frknkrc44/HMA-OSS?label=Release">
    </a>
    <a href="https://apt.izzysoft.de/fdroid/index/apk/org.frknkrc44.hma_oss" style="text-decoration:none">
      <img src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/org.frknkrc44.hma_oss&label=IzzyOnDroid">
    </a>
    <a href="https://shields.rbtlog.dev/org.frknkrc44.hma_oss" style="text-decoration:none">
      <img src="https://shields.rbtlog.dev/simple/org.frknkrc44.hma_oss">
    </a>
    <a href="https://github.com/frknkrc44/HMA-OSS/releases/latest" style="text-decoration:none">
      <img src="https://img.shields.io/github/downloads/frknkrc44/HMA-OSS/total">
    </a>
    <a href="https://t.me/aerathfuns" style="text-decoration:none">
      <img src="https://img.shields.io/badge/Telegram-Channel-blue.svg?logo=telegram">
    </a>
    <a href="https://choosealicense.com/licenses/gpl-3.0/" style="text-decoration:none">
      <img src="https://img.shields.io/github/license/frknkrc44/HMA-OSS?label=License">
    </a>
    <a href="https://hypercommit.com/hma-oss">
      <img src="https://img.shields.io/badge/Hypercommit-DB2475">
    </a>
  </p>
</div>

---

- **English**
- [中文（简体）](README_zh_CN.md)
- [Türkçe](README_tr.md)
- [日本語](README_ja.md)
- [Indonesia](README_id.md)

## About this module

Although it's bad practice to detect the installation of specific apps, not every app using root provides random package name support. In this case, if apps related to root (such as Fake Location and Storage Isolation) are detected, it is tantamount to detecting that the device is rooted.

Additionally, some apps use various loopholes to acquire your app list, in order to use it as fingerprinting data or for other nefarious purposes.

This module can work as an Xposed module to hide apps or reject app list requests.

## Xposed API

HMA-OSS is built against the **Modern Xposed API** (`io.github.libxposed:api`, libxposed
102.0.0) instead of the legacy `de.robv.android.xposed` bridge:

* the entry point extends `io.github.libxposed.api.XposedModule` and is declared in
  `META-INF/xposed/java_init.list`;
* module metadata lives in `META-INF/xposed/module.prop`, the scope is the single
  `system` package in `META-INF/xposed/scope.list`, so the module is injected into
  `system_server` where the package manager, activity manager, accessibility, input
  method, storage and settings provider hooks run;
* hooks use the interceptor chain model (`Hooker` / `Chain`) instead of
  `XC_MethodHook`, and `EzXHelper` has been replaced by the small reflection layer in
  `icu.nullptr.hidemyapplist.xposed.bridge`.

### Hot reload (API 102)

Hot reload is an API 102 feature and is fully supported: `XposedEntry.onHotReloading`
stops the module owned threads and unhooks everything, hands class loader neutral
binder references to the next generation, and `XposedEntry.onHotReloaded` rebuilds the
hooks in the new code.

The module declares `minApiVersion=101` and `targetApiVersion=102`. On an **API 101
framework hot reload is disabled**: `getApiVersion()` is checked at runtime, API 102
only calls such as `HookBuilder.setId`, `HookHandle.getId` and `HookHandle.replaceHook`
are skipped, and `onHotReloading` returns `false` so the framework keeps running the
current generation.

The manager app connects to the framework through
`io.github.libxposed:service` (`XposedServiceHelper` / `XposedService`), which is what
the *Hot reload module* entry in the log screen uses; the HMA-OSS specific
`IHMAService` binder is still used to exchange the configuration with the hooks.

## About HMA-OSS

https://github.com/frknkrc44/HMA-OSS/wiki

## I want to contribute translation
You can contribute translation [here](https://crowdin.com/project/frknkrc44-hma-oss).

## Update log
[Reference to the commits page](https://github.com/frknkrc44/HMA-OSS/commits)
