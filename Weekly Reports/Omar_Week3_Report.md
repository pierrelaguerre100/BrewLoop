# Omar's Input – Week 3

This week, I extended the shared cart so that orders can store drink customization options. CartItem now includes size, milkType, and extraShots fields with getters, along with a matches(itemId, size, milkType, extraShots) method to determine whether two cart items have the same item ID and customization options.

In Cart, I added a NOT_APPLICABLE constant ("n/a") and a six-argument add(itemId, name, unitPrice, size, milkType, extraShots) method. This method only merges an item with an existing cart line when the item and all its customization options match. The original three-argument add method still works for items without customization options, such as pastries. I also updated remove(int position) to ignore invalid positions.

I wrote CartTest with 13 JUnit tests covering adding items, merging matching items, keeping items with different options in separate lines, getTotal, getLineTotal, remove, clear, and matches. All 13 tests passed.

I also merged main into feature-cart and resolved a conflict in AndroidManifest.xml to ensure the manager menu, cart, and rewards screens are registered correctly. Draft pull request #3 now merges cleanly. Additionally, I reviewed Pierre's pull request #6 and noticed that his CheckoutActivity maintains its own cart list instead of using the shared Cart, so I asked him to update it to use the shared implementation.

I also updated CartAdapter to display each cart item's size, milk type, and extra shots when available. This makes it easier for customers to review their selections in the cart. I tested the cart by adding multiple items and verifying that the item details, quantities, prices, and remove functionality worked correctly. I committed and pushed these changes to feature-cart.

## For Next Week

After pull request #3 is merged, I will help Pierre update CheckoutActivity and CustomizationActivity to use the shared Cart and agree on a consistent set of pickup hours. I will also connect orders to the signed-in user's ID and add Javadoc and string resources to my classes.