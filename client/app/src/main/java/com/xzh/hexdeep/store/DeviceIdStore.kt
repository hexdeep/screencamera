package com.xzh.hexdeep.store

import android.content.Context
import com.xzh.hexdeep.App
import com.xzh.hexdeep.model.DeviceItem
import org.json.JSONArray
import org.json.JSONObject

object DeviceIdStore {

    private const val PREF_NAME = "app_config"
    private const val KEY_DEVICES = "devices"

    /** 用 Map 防止重复 id */
    private val devices = mutableMapOf<String, DeviceItem>()

    fun init() {
        load()
    }

    /** 按创建时间倒序返回 */
    fun getAll(): List<String> {
        return devices.values
            .sortedByDescending { it.createdAt }
            .map { it.id }
    }

    fun getDeviceItemById(id: String): DeviceItem? {
        return devices[id]
    }

    fun add(id: String, remark: String = "") {
        if (devices.containsKey(id)) return

        devices[id] = DeviceItem(
            id = id,
            remark = remark,
            createdAt = System.currentTimeMillis()
        )
        save()
    }

    fun updateRemark(id: String, remark: String) {
        val old = devices[id] ?: return
        devices[id] = old.copy(remark = remark)
        save()
    }

    fun remove(id: String) {
        if (devices.remove(id) != null) {
            save()
        }
    }

    fun get(id: String): DeviceItem? = devices[id]

    fun contains(id: String): Boolean = devices.containsKey(id)

    /** ---------------- private ---------------- */

    private fun sp(): Context {
        return App.instance.applicationContext
    }

    private fun load() {
        devices.clear()

        val sp = sp().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_DEVICES, null) ?: return

        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val item = DeviceItem(
                id = obj.getString("id"),
                remark = obj.optString("remark"),
                createdAt = obj.optLong("createdAt")
            )
            devices[item.id] = item
        }
    }

    private fun save() {
        val array = JSONArray()
        devices.values.forEach {
            val obj = JSONObject().apply {
                put("id", it.id)
                put("remark", it.remark)
                put("createdAt", it.createdAt)
            }
            array.put(obj)
        }

        val sp = sp().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_DEVICES, array.toString())
            .apply()
    }
}
