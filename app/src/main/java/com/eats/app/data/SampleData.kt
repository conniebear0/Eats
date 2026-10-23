package com.eats.app.data

import com.eats.app.R

/**
 * Placeholder content so the screens have something to show.
 * Replace with your API / database. The UI only depends on the classes in Models.kt.
 */
object SampleData {

    private fun choices(vararg pairs: Pair<String, Int>) = pairs.map { OptionChoice(it.first, it.second) }

    private val meal = OptionGroup(
        "meal", "Make it a meal", multi = false, hideDefault = true,
        choices = choices("Item only" to 0, "With slap chips + cooldrink" to 32, "Large chips + large cooldrink" to 44),
    )
    private val extras = OptionGroup(
        "extras", "Extras", multi = true,
        choices = choices("Extra cheese" to 8, "Crispy bacon" to 14, "Fried egg" to 9, "Peri-peri drizzle" to 5),
    )
    private val kotaExtras = OptionGroup(
        "kotaExtras", "Extras", multi = true,
        choices = choices("Extra polony" to 7, "Extra atchar" to 5, "Extra cheese" to 8),
    )
    private val spice = OptionGroup(
        "spice", "Spice level", multi = false,
        choices = choices("Mild" to 0, "Medium" to 0, "Hot" to 0, "Extra hot" to 0),
    )
    private val size = OptionGroup(
        "size", "Size", multi = false,
        choices = choices("Regular" to 0, "Large" to 18),
    )
    private val base = OptionGroup(
        "base", "Base", multi = false,
        choices = choices("Classic" to 0, "Thin and crispy" to 0, "Stuffed crust" to 22),
    )
    private val sauce = OptionGroup(
        "sauce", "Dipping sauce", multi = false,
        choices = choices("Garlic" to 0, "Peri-peri" to 0, "Sweet chilli" to 0),
    )
    private val milk = OptionGroup(
        "milk", "Milk", multi = false,
        choices = choices("Full cream" to 0, "Oat" to 6),
    )

    private fun dish(
        id: String, name: String, description: String, price: Int, art: DishArt,
        options: List<OptionGroup> = emptyList(), tag: String? = null, photo: Int? = null,
    ) = Dish(id, name, description, price, art, options, tag, photo)

    val categories = listOf(
        Category("kota", "Kotas", DishArt.Gatsby),
        Category("burgers", "Burgers", DishArt.Burger),
        Category("pizza", "Pizza", DishArt.Pizza),
        Category("chicken", "Chicken", DishArt.Chicken),
        Category("braai", "Braai", DishArt.Braai),
        Category("sushi", "Sushi", DishArt.Sushi),
        Category("indian", "Indian", DishArt.Chicken),
        Category("desserts", "Sweet", DishArt.Koek),
    )

    val restaurants: List<Restaurant> = listOf(
        Restaurant(
            id = "kota", name = "Kota King", cuisine = "Kotas, Gatsbys, street food", area = "Quigney",
            art = DishArt.Gatsby, categories = setOf("kota"), rating = 4.7, reviews = "1,200+",
            deliveryFee = 9, etaMinutes = 18, freeDeliveryOver = 150,
            tags = listOf("Great value", "1000+ reordered"),
            menu = listOf(
                MenuSection("Kotas", listOf(
                    dish("k1", "Classic Kota", "Quarter loaf with slap chips, polony, cheese, atchar and tomato sauce.", 45, DishArt.Bread, listOf(kotaExtras), "#1 most liked"),
                    dish("k2", "Russian and Egg Kota", "Spicy russian, fried egg, chips, cheese and atchar.", 52, DishArt.Bread, listOf(kotaExtras)),
                    dish("k3", "Full House Kota", "Russian, polony, egg, bacon, cheese, chips and atchar.", 68, DishArt.Bread, listOf(kotaExtras), "Great value"),
                )),
                MenuSection("Gatsbys", listOf(
                    dish("k4", "Steak Gatsby", "Footlong roll stacked with sliced steak, chips, lettuce and peri-peri mayo.", 89, DishArt.Gatsby, listOf(extras), "Feeds 2"),
                    dish("k5", "Masala Chicken Gatsby", "Masala chicken strips, chips, tomato and sambal.", 85, DishArt.Gatsby, listOf(extras)),
                    dish("k6", "Chips and Polony Gatsby", "The after-school classic.", 55, DishArt.Gatsby),
                )),
                MenuSection("Sides", listOf(
                    dish("k7", "Slap Chips", "Soft, salty, vinegar optional.", 25, DishArt.Fries, listOf(size)),
                    dish("k8", "Vetkoek and Mince", "Fried dough, curried mince.", 38, DishArt.Bread),
                )),
                MenuSection("Drinks", listOf(
                    dish("k9", "Ginger Beer 440ml", "Sharp and cold.", 18, DishArt.Drink),
                    dish("k10", "Iced Rooibos", "Peach and lemon.", 22, DishArt.Drink),
                )),
            ),
        ),
        Restaurant(
            id = "smash", name = "Smash and Co", cuisine = "Burgers, shakes", area = "Vincent",
            art = DishArt.Burger, categories = setOf("burgers"), rating = 4.6, reviews = "900+",
            deliveryFee = 12, etaMinutes = 22, freeDeliveryOver = 200, tags = listOf("Great value"),
            photo = R.drawable.classic_burger,
            menu = listOf(
                MenuSection("Burgers", listOf(
                    dish("s1", "Smash Single", "Smashed beef patty, cheddar, pickles, house sauce.", 59, DishArt.Burger, listOf(meal, extras), "#1 most liked", photo = R.drawable.classic_burger),
                    dish("s2", "Double Smash Stack", "Two patties, double cheddar, caramelised onion.", 82, DishArt.Burger, listOf(meal, extras)),
                    dish("s3", "Chicken Crunch", "Buttermilk fried chicken, slaw, hot honey.", 69, DishArt.Burger, listOf(meal, extras)),
                    dish("s4", "Bean and Beet Burger", "Plant-based patty, beetroot relish, rocket.", 64, DishArt.Burger, listOf(meal)),
                )),
                MenuSection("Sides", listOf(
                    dish("s5", "Loaded Fries", "Cheese sauce, bacon bits, spring onion.", 42, DishArt.Fries, photo = R.drawable.chips),
                    dish("s6", "Onion Rings", "Beer-battered, with smoky mayo.", 30, DishArt.Fries),
                    dish("s7", "Slap Chips", "Skin-on, salted.", 25, DishArt.Fries, listOf(size), photo = R.drawable.chips),
                )),
                MenuSection("Shakes", listOf(
                    dish("s8", "Vanilla Bean Shake", "Thick and cold.", 38, DishArt.Drink, photo = R.drawable.milkshake),
                    dish("s9", "Chocolate Shake", "Dark cocoa, whipped cream.", 38, DishArt.Drink),
                )),
            ),
        ),
        Restaurant(
            id = "braai", name = "Braai Republic", cuisine = "Braai plates, pap and wors", area = "Berea",
            art = DishArt.Braai, categories = setOf("braai"), rating = 4.5, reviews = "800+",
            deliveryFee = 15, etaMinutes = 27, freeDeliveryOver = 220, tags = listOf("2000+ reordered"),
            menu = listOf(
                MenuSection("Braai Plates", listOf(
                    dish("b1", "Boerewors and Pap", "Coiled wors, creamy pap, chakalaka.", 78, DishArt.Braai, tag = "#1 most liked"),
                    dish("b2", "Lamb Chops Plate", "Four chops, pap or chips, gravy.", 125, DishArt.Braai),
                    dish("b3", "Mixed Grill for Two", "Wors, chops, chicken sosatie, pap, salads.", 245, DishArt.Braai, tag = "Feeds 2"),
                )),
                MenuSection("Rolls", listOf(
                    dish("b4", "Wors Roll", "Grilled wors, onion gravy, mustard.", 42, DishArt.Gatsby, listOf(extras)),
                    dish("b5", "Steak Roll", "Rump strips, caramelised onion, cheese.", 58, DishArt.Gatsby, listOf(extras)),
                )),
                MenuSection("Drinks", listOf(
                    dish("b6", "Ginger Beer 440ml", "Sharp and cold.", 18, DishArt.Drink),
                    dish("b7", "Iced Rooibos", "Peach and lemon.", 22, DishArt.Drink),
                )),
            ),
        ),
        Restaurant(
            id = "napoli", name = "Napoli on Moore", cuisine = "Wood-fired pizza", area = "Nahoon",
            art = DishArt.Pizza, categories = setOf("pizza"), rating = 4.4, reviews = "600+",
            deliveryFee = 14, etaMinutes = 30, freeDeliveryOver = 0, tags = emptyList(),
            photo = R.drawable.margherita_pizza,
            menu = listOf(
                MenuSection("Pizzas", listOf(
                    dish("p1", "Margherita", "Tomato, fior di latte, basil.", 79, DishArt.Pizza, listOf(base), "#1 most liked", photo = R.drawable.margherita_pizza),
                    dish("p2", "Pepperoni", "Spicy pepperoni, mozzarella, oregano.", 94, DishArt.Pizza, listOf(base), photo = R.drawable.pepperoni_pizza),
                    dish("p3", "Boerewors BBQ", "Wors, caramelised onion, BBQ base, mozzarella.", 99, DishArt.Pizza, listOf(base), "Local twist"),
                )),
                MenuSection("Sides", listOf(
                    dish("p4", "Garlic Bread", "Rosemary, sea salt, garlic butter.", 35, DishArt.Bread, photo = R.drawable.garlic_bread),
                )),
            ),
        ),
        Restaurant(
            id = "sakura", name = "Sakura Bay", cuisine = "Sushi and rolls", area = "Beacon Bay",
            art = DishArt.Sushi, categories = setOf("sushi"), rating = 4.8, reviews = "1,500+",
            deliveryFee = 20, etaMinutes = 35, freeDeliveryOver = 300, tags = listOf("Great value"),
            photo = R.drawable.salmon_roll,
            menu = listOf(
                MenuSection("Rolls", listOf(
                    dish("r1", "Salmon Roll 8pc", "Salmon, cucumber, cream cheese.", 89, DishArt.Sushi, tag = "#1 most liked", photo = R.drawable.salmon_roll),
                    dish("r2", "Avo Roll 8pc", "Avocado, cucumber, sesame.", 69, DishArt.Sushi),
                    dish("r6", "California Roll 8pc", "Crab, avocado, cucumber, sesame.", 85, DishArt.Sushi, photo = R.drawable.california_roll),
                    dish("r3", "Dragon Roll 8pc", "Prawn tempura, avocado, eel sauce.", 119, DishArt.Sushi),
                )),
                MenuSection("Starters", listOf(
                    dish("r5", "Miso Soup", "Tofu and seaweed.", 30, DishArt.Drink, photo = R.drawable.miso_soup),
                )),
                MenuSection("Platters", listOf(
                    dish("r4", "Sushi for Two", "24 pieces, ginger, wasabi, soy.", 289, DishArt.Sushi, tag = "Feeds 2"),
                )),
            ),
        ),
        Restaurant(
            id = "peri", name = "Peri Peri Yard", cuisine = "Flame-grilled chicken", area = "Southernwood",
            art = DishArt.Chicken, categories = setOf("chicken"), rating = 4.3, reviews = "700+",
            deliveryFee = 10, etaMinutes = 20, freeDeliveryOver = 180, tags = listOf("2000+ reordered"),
            menu = listOf(
                MenuSection("Chicken", listOf(
                    dish("c1", "Quarter Chicken", "Flame-grilled, basted to order.", 59, DishArt.Chicken, listOf(spice), "#1 most liked"),
                    dish("c2", "Half Chicken", "With a side of your choice.", 98, DishArt.Chicken, listOf(spice)),
                    dish("c3", "Wings 10pc", "Crispy, tossed in sauce.", 89, DishArt.Chicken, listOf(spice, sauce)),
                )),
                MenuSection("Wraps", listOf(
                    dish("c4", "Peri Wrap", "Grilled chicken, lettuce, tomato, mayo.", 62, DishArt.Wrap, listOf(spice)),
                )),
                MenuSection("Sides", listOf(
                    dish("c5", "Spicy Rice", "Rice with peppers.", 28, DishArt.Braai),
                    dish("c6", "Slap Chips", "Skin-on, salted.", 25, DishArt.Fries, listOf(size)),
                )),
            ),
        ),
        Restaurant(
            id = "sugar", name = "Sugar Cane Bakery", cuisine = "Koeksisters, tarts, coffee", area = "Selborne",
            art = DishArt.Koek, categories = setOf("desserts"), rating = 4.9, reviews = "450+",
            deliveryFee = 8, etaMinutes = 15, freeDeliveryOver = 0, tags = listOf("Great value"),
            menu = listOf(
                MenuSection("Sweet things", listOf(
                    dish("d1", "Koeksisters x3", "Syrupy, braided, still warm.", 30, DishArt.Koek, tag = "#1 most liked"),
                    dish("d2", "Milk Tart Slice", "Cinnamon on top.", 28, DishArt.Tart),
                    dish("d3", "Malva Pudding", "Apricot sponge, hot custard.", 42, DishArt.Tart),
                )),
                MenuSection("Coffee", listOf(
                    dish("d4", "Rooibos Latte", "Steamed milk, honey.", 32, DishArt.Drink, listOf(milk)),
                )),
            ),
        ),
        Restaurant(
            id = "spice", name = "Spice Route", cuisine = "Indian curries and snacks", area = "Gonubie",
            art = DishArt.Chicken, categories = setOf("indian"), rating = 4.4, reviews = "500+",
            deliveryFee = 16, etaMinutes = 40, freeDeliveryOver = 250, tags = listOf("Great value"),
            photo = R.drawable.butter_chicken,
            menu = listOf(
                MenuSection("Curries", listOf(
                    dish("i1", "Butter Chicken", "Creamy tomato curry with basmati rice.", 110, DishArt.Chicken, listOf(spice), "#1 most liked", photo = R.drawable.butter_chicken),
                    dish("i2", "Vegetable Curry", "Seasonal vegetables with naan.", 90, DishArt.Bread, listOf(spice), "Veg", photo = R.drawable.vegetable_curry),
                )),
                MenuSection("Snacks", listOf(
                    dish("i3", "Samosas", "Pack of 4, crisp and spiced.", 40, DishArt.Bread, photo = R.drawable.samosas),
                )),
            ),
        ),
        Restaurant(
            id = "bunny", name = "Bunny Bowl", cuisine = "Bunny chows and curries", area = "Quigney",
            art = DishArt.Bread, categories = setOf("kota", "braai"), rating = 4.6, reviews = "1,000+",
            deliveryFee = 12, etaMinutes = 25, freeDeliveryOver = 200, tags = emptyList(),
            menu = listOf(
                MenuSection("Bunny Chows", listOf(
                    dish("u1", "Bean Bunny Quarter", "Sugar bean curry in a quarter loaf.", 48, DishArt.Bread, listOf(spice), "Veg"),
                    dish("u2", "Mutton Bunny Half", "Slow-cooked mutton curry, sambals.", 95, DishArt.Bread, listOf(spice), "#1 most liked"),
                    dish("u3", "Chicken Curry Bunny", "Potato and chicken curry.", 70, DishArt.Bread, listOf(spice)),
                )),
                MenuSection("Wraps", listOf(
                    dish("u4", "Butter Chicken Wrap", "Grilled roti, butter chicken, carrot sambal.", 65, DishArt.Wrap),
                )),
            ),
        ),
    )

    fun restaurant(id: String): Restaurant? = restaurants.firstOrNull { it.id == id }

    /** Restaurants whose name, cuisine, area or any dish name contains every word of [query]. */
    fun search(query: String): List<Restaurant> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return restaurants.filter { r ->
            val haystack = buildString {
                append(r.name).append(' ').append(r.cuisine).append(' ').append(r.area).append(' ')
                r.dishes.forEach { append(it.name).append(' ') }
            }.lowercase()
            words.all { it in haystack }
        }
    }
}
