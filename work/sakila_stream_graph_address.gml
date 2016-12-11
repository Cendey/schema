graph [
	ui.quality "true"
	ui.antialias "true"
	ui.stylesheet "url(css/polish.css)"
	ui.default.title "address"
	node [
		id "naddress"
		ui.class "root"
		ui.label "address"
		Component 1
		data.child "[ncustomer, nstaff, nstore]"
		ui.style " size:11px;"
	]
	node [
		id "ncustomer"
		data.parent "[naddress, nstore]"
		ui.label "customer"
		Component 1
		data.child "[npayment, nrental]"
		ui.style " size:14px;"
	]
	node [
		id "nstaff"
		data.parent "[naddress, nstore]"
		ui.label "staff"
		Component 1
		data.child "[npayment, nrental, nstore]"
		ui.style " size:16px;"
	]
	node [
		id "nstore"
		data.parent "[naddress, nstaff]"
		ui.label "store"
		Component 1
		data.child "[ncustomer, ninventory, nstaff]"
		ui.style " size:16px;"
	]
	node [
		id "npayment"
		data.parent "[ncustomer, nrental, nstaff]"
		ui.class "leaf"
		ui.label "payment"
		Component 1
		ui.style " size:11px;"
	]
	node [
		id "nrental"
		data.parent "[ncustomer, ninventory, nstaff]"
		ui.label "rental"
		Component 1
		data.child "[npayment]"
		ui.style " size:14px;"
	]
	node [
		id "ninventory"
		data.parent "[nfilm, nstore]"
		ui.label "inventory"
		Component 1
		data.child "[nrental]"
		ui.style " size:11px;"
	]
	node [
		id "nfilm"
		data.parent "[nlanguage]"
		ui.label "film"
		Component 1
		data.child "[nfilm_actor, nfilm_category, ninventory]"
		ui.style " size:14px;"
	]
	node [
		id "nfilm_actor"
		data.parent "[nfilm, nactor]"
		ui.class "leaf"
		ui.label "film_actor"
		Component 1
		ui.style " size:9px;"
	]
	node [
		id "nfilm_category"
		data.parent "[ncategory, nfilm]"
		ui.class "leaf"
		ui.label "film_category"
		Component 1
		ui.style " size:9px;"
	]
	node [
		id "nlanguage"
		ui.class "root"
		ui.label "language"
		Component 1
		data.child "[nfilm]"
		ui.style " size:6px;"
	]
	node [
		id "nactor"
		ui.class "root"
		ui.label "actor"
		Component 1
		data.child "[nfilm_actor]"
		ui.style " size:6px;"
	]
	node [
		id "ncategory"
		ui.class "root"
		ui.label "category"
		Component 1
		data.child "[nfilm_category]"
		ui.style " size:6px;"
	]
	edge [
		id "eaddresscustomer"
		source "ncustomer"
		target "naddress"
		ui.label "[address_id]"
		ui.class "root"
	]
	edge [
		id "eaddressstaff"
		source "nstaff"
		target "naddress"
		ui.label "[address_id]"
		ui.class "root"
	]
	edge [
		id "eaddressstore"
		source "nstore"
		target "naddress"
		ui.label "[address_id]"
		ui.class "root"
	]
	edge [
		id "ecustomerpayment"
		source "npayment"
		target "ncustomer"
		ui.label "[customer_id]"
		ui.class "leaf"
	]
	edge [
		id "ecustomerrental"
		source "nrental"
		target "ncustomer"
		ui.label "[customer_id]"
	]
	edge [
		id "estaffpayment"
		source "npayment"
		target "nstaff"
		ui.label "[staff_id]"
		ui.class "leaf"
	]
	edge [
		id "estaffrental"
		source "nrental"
		target "nstaff"
		ui.label "[staff_id]"
	]
	edge [
		id "estaffstore"
		source "nstore"
		target "nstaff"
		ui.label "[manager_staff_id]"
	]
	edge [
		id "estorecustomer"
		source "ncustomer"
		target "nstore"
		ui.label "[store_id]"
	]
	edge [
		id "estoreinventory"
		source "ninventory"
		target "nstore"
		ui.label "[store_id]"
	]
	edge [
		id "estorestaff"
		source "nstaff"
		target "nstore"
		ui.label "[store_id]"
	]
	edge [
		id "erentalpayment"
		source "npayment"
		target "nrental"
		ui.label "[rental_id]"
		ui.class "leaf"
	]
	edge [
		id "einventoryrental"
		source "nrental"
		target "ninventory"
		ui.label "[inventory_id]"
	]
	edge [
		id "efilmfilm_actor"
		source "nfilm_actor"
		target "nfilm"
		ui.label "[film_id]"
		ui.class "leaf"
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
		id "elanguagefilm"
		source "nfilm"
		target "nlanguage"
		ui.label "[language_id |  original_language_id]"
		ui.class "root"
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
]
