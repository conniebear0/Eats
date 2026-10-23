package com.eats.app.order

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One line of a finished order, kept as plain text so history needs no menu lookup. */
data class PastLine(val name: String, val qty: Int, val total: Int)

/** A delivered order shown in the Orders tab. */
data class PastOrder(
    val id: String,
    val restaurantId: String,
    val restaurantName: String,
    val placedAt: Long,
    val total: Int,
    val pickup: Boolean,
    val lines: List<PastLine>,
    val rating: Int = 0,
    /** Added for the support chat. Older saved orders don't have them, so they have defaults. */
    val deliveredAt: Long = 0,
    val pay: String = "Cash",
    val address: String = "",
)

/** Everything remembered about one signed-in user. */
data class UserData(
    val address: String = "",
    val favourites: Set<String> = emptySet(),
    val history: List<PastOrder> = emptyList(),
)

/** Where per-user data is kept. Swap [LocalUserDataStore] for an API-backed class when a server exists. */
interface UserDataStore {
    fun load(userId: String): UserData
    fun save(userId: String, data: UserData)
}

/** Keeps nothing between runs. Used by the previews. */
class InMemoryUserDataStore : UserDataStore {
    private val data = mutableMapOf<String, UserData>()
    override fun load(userId: String) = data[userId] ?: UserData()
    override fun save(userId: String, data: UserData) {
        this.data[userId] = data
    }
}

/** Saves each user's address, favourites and order history on the device, keyed by user id. */
class LocalUserDataStore(context: Context) : UserDataStore {
    private val prefs = context.applicationContext.getSharedPreferences("eats_user_data", Context.MODE_PRIVATE)

    override fun load(userId: String): UserData {
        val json = prefs.getString(userId, null) ?: return UserData()
        return runCatching {
            val o = JSONObject(json)
            UserData(
                address = o.optString("address"),
                favourites = o.optJSONArray("favourites").strings().toSet(),
                history = o.optJSONArray("history").objects().map(::toPastOrder),
            )
        }.getOrDefault(UserData()) // a damaged record should not stop the app
    }

    override fun save(userId: String, data: UserData) {
        val o = JSONObject()
            .put("address", data.address)
            .put("favourites", JSONArray(data.favourites.toList()))
            .put("history", JSONArray(data.history.map(::fromPastOrder)))
        prefs.edit().putString(userId, o.toString()).apply()
    }

    private fun fromPastOrder(p: PastOrder) = JSONObject()
        .put("id", p.id)
        .put("restaurantId", p.restaurantId)
        .put("restaurantName", p.restaurantName)
        .put("placedAt", p.placedAt)
        .put("total", p.total)
        .put("pickup", p.pickup)
        .put("rating", p.rating)
        .put("deliveredAt", p.deliveredAt)
        .put("pay", p.pay)
        .put("address", p.address)
        .put(
            "lines",
            JSONArray(p.lines.map { JSONObject().put("name", it.name).put("qty", it.qty).put("total", it.total) }),
        )

    private fun toPastOrder(o: JSONObject) = PastOrder(
        id = o.getString("id"),
        restaurantId = o.getString("restaurantId"),
        restaurantName = o.getString("restaurantName"),
        placedAt = o.getLong("placedAt"),
        total = o.getInt("total"),
        pickup = o.getBoolean("pickup"),
        rating = o.optInt("rating"),
        deliveredAt = o.optLong("deliveredAt"),
        pay = o.optString("pay", "Cash"),
        address = o.optString("address"),
        lines = o.getJSONArray("lines").objects().map {
            PastLine(it.getString("name"), it.getInt("qty"), it.getInt("total"))
        },
    )

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
