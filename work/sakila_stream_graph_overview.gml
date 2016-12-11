graph [
	node [
		id "ncountry"
		ui.label "country"
		Component 0
		data.child "[ncity]"
	]
	node [
		id "ncity"
		ui.label "city"
		Component 0
		data.parent "[ncountry]"
	]
	node [
		id "naddress"
		ui.label "address"
		Component 1
		data.child "[ncustomer, nstaff, nstore]"
	]
	node [
		id "ncustomer"
		data.parent "[naddress, nstore]"
		ui.label "customer"
		Component 1
		data.child "[npayment, nrental]"
	]
	node [
		id "nstore"
		data.parent "[naddress, nstaff]"
		ui.label "store"
		Component 1
		data.child "[ncustomer, ninventory, nstaff]"
	]
	node [
		id "nlanguage"
		ui.label "language"
		Component 1
		data.child "[nfilm]"
	]
	node [
		id "nfilm"
		data.parent "[nlanguage]"
		ui.label "film"
		Component 1
		data.child "[nfilm_actor, nfilm_category, ninventory]"
	]
	node [
		id "nfilm_actor"
		ui.label "film_actor"
		Component 1
		data.parent "[nfilm, nactor]"
	]
	node [
		id "nactor"
		ui.label "actor"
		Component 1
		data.child "[nfilm_actor]"
	]
	node [
		id "ncategory"
		ui.label "category"
		Component 1
		data.child "[nfilm_category]"
	]
	node [
		id "nfilm_category"
		data.parent "[ncategory, nfilm]"
		ui.class "leaf"
		ui.label "film_category"
		Component 1
	]
	node [
		id "ninventory"
		data.parent "[nfilm, nstore]"
		ui.label "inventory"
		Component 1
		data.child "[nrental]"
	]
	node [
		id "npayment"
		data.parent "[ncustomer, nrental, nstaff]"
		ui.class "leaf"
		ui.label "payment"
		Component 1
	]
	node [
		id "nrental"
		data.parent "[ncustomer, ninventory, nstaff]"
		ui.label "rental"
		Component 1
		data.child "[npayment]"
	]
	node [
		id "nstaff"
		data.parent "[naddress, nstore]"
		ui.label "staff"
		Component 1
		data.child "[npayment, nrental, nstore]"
	]
	edge [
		id "ecountrycity"
		source "ncity"
		target "ncountry"
		ui.label "[country_id]"
		ui.class "root"
	]
	edge [
		id "eaddresscustomer"
		source "ncustomer"
		target "naddress"
		ui.label "[address_id]"
		ui.class "root"
	]
	edge [
		id "estorecustomer"
		source "ncustomer"
		target "nstore"
		ui.label "[store_id]"
	]
	edge [
		id "elanguagefilm"
		source "nfilm"
		target "nlanguage"
		ui.label "[language_id |  original_language_id]"
		ui.class "root"
	]
	edge [
		id "efilmfilm_actor"
		source "nfilm_actor"
		target "nfilm"
		ui.label "[film_id]"
		ui.class "leaf"
	]
	edge [
		id "eactorfilm_actor"
		source "nfilm_actor"
		target "nactor"
		ui.label "[actor_id]"
		ui.class "root"
	]
	edge [
		id "ecategoryfilm_category"
		source "nfilm_category"
		target "ncategory"
		ui.label "[category_id]"
		ui.class "root"
	]
	edge [
		id "efilmfilm_category"
		source "nfilm_category"
		target "nfilm"
		ui.label "[film_id]"
		ui.class "leaf"
	]
	edge [
		id "efilminventory"
		source "ninventory"
		target "nfilm"
		ui.label "[film_id]"
	]
	edge [
		id "estoreinventory"
		source "ninventory"
		target "nstore"
		ui.label "[store_id]"
	]
	edge [
		id "ecustomerpayment"
		source "npayment"
		target "ncustomer"
		ui.label "[customer_id]"
		ui.class "leaf"
	]
	edge [
		id "erentalpayment"
		source "npayment"
		target "nrental"
		ui.label "[rental_id]"
		ui.class "leaf"
	]
	edge [
		id "estaffpayment"
		source "npayment"
		target "nstaff"
		ui.label "[staff_id]"
		ui.class "leaf"
	]
	edge [
		id "ecustomerrental"
		source "nrental"
		target "ncustomer"
		ui.label "[customer_id]"
	]
	edge [
		id "einventoryrental"
		source "nrental"
		target "ninventory"
		ui.label "[inventory_id]"
	]
	edge [
		id "estaffrental"
		source "nrental"
		target "nstaff"
		ui.label "[staff_id]"
	]
	edge [
		id "eaddressstaff"
		source "nstaff"
		target "naddress"
		ui.label "[address_id]"
		ui.class "root"
	]
	edge [
		id "estorestaff"
		source "nstaff"
		target "nstore"
		ui.label "[store_id]"
	]
	edge [
		id "eaddressstore"
		source "nstore"
		target "naddress"
		ui.label "[address_id]"
		ui.class "root"
	]
	edge [
		id "estaffstore"
		source "nstore"
		target "nstaff"
		ui.label "[manager_staff_id]"
	]
]
