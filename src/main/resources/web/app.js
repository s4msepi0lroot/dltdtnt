// sepiolSMP web profiles. Talks only to the plugin API on this very port.

const $ = (id) => document.getElementById(id);

async function api(path) {
	const response = await fetch(path, { cache: "no-store" });
	if (!response.ok) throw new Error(response.status);
	return response.json();
}

// Avatar: for now a public head renderer with a graceful fallback.
// Brick 2 is live: SepiolSkins renders the heads and SepiolCore serves them on this
// very port, so the profile page needs no external avatar service at all.
function avatarUrl(name) {
	return "/avatar/" + encodeURIComponent(name) + ".png";
}

function hours(value) {
	return (value ?? 0).toFixed(1).replace(".0", "") + " ч";
}

function ago(millis) {
	if (!millis) return "нет данных";
	const minutes = Math.floor((Date.now() - millis) / 60000);
	if (minutes < 2) return "только что";
	if (minutes < 60) return minutes + " мин назад";
	const hrs = Math.floor(minutes / 60);
	if (hrs < 24) return hrs + " ч назад";
	return Math.floor(hrs / 24) + " дн назад";
}

function row(label, value) {
	return `<div class="row"><span>${label}</span><b>${value}</b></div>`;
}

function bar(percent) {
	const width = Math.max(0, Math.min(100, percent ?? 0));
	return `<div class="bar"><i style="width:${width}%"></i></div>`;
}

const WITHDRAWAL = ["нет", "лёгкая", "средняя", "сильная", "критическая"];
const DRUNK = ["трезв", "навеселе", "пьян", "вдрызг"];

function drunkStage(percent) {
	if (percent >= 60) return DRUNK[3];
	if (percent >= 30) return DRUNK[2];
	if (percent > 2) return DRUNK[1];
	return DRUNK[0];
}

function renderProfile(profile) {
	$("profile-panel").hidden = false;
	$("profile-name").textContent = profile.name;
	$("profile-sub").innerHTML = (profile.online
		? `<span class="on">● онлайн</span> · мир ${profile.world ?? "—"}`
		: `был в игре ${ago(profile.lastSeen)}`) + ` · <code>${profile.uuid}</code>`;

	const avatar = $("avatar");
	avatar.onerror = () => { avatar.onerror = null; avatar.src = "data:image/svg+xml," + encodeURIComponent(
		`<svg xmlns="http://www.w3.org/2000/svg" width="72" height="72"><rect width="72" height="72" fill="#2a2437"/><text x="36" y="47" font-size="32" fill="#a970ff" text-anchor="middle" font-family="sans-serif">${(profile.name[0] || "?").toUpperCase()}</text></svg>`); };
	avatar.src = avatarUrl(profile.name);

	const cards = [];
	const v = profile.vanilla ?? {};
	cards.push(`<div class="card"><h4>Сезон</h4>
		${row("Наиграно", hours(v.hours))}
		${row("Смертей", v.deaths ?? 0)}
		${row("Без смерти", hours(v.hoursSinceDeath))}
		${row("Мобов убито", v.mobKills ?? 0)}
		${row("PvP убийств", v.playerKills ?? 0)}
		${row("Блоков добыто", (v.blocksMined ?? 0).toLocaleString("ru"))}
		${row("Пройдено", (v.kmWalked ?? 0) + " км")}
	</div>`);

	if (profile.badhabits) {
		const nic = profile.badhabits.nicotine ?? {};
		const nar = profile.badhabits.narcotic ?? {};
		cards.push(`<div class="card bad"><h4>Bad Habits</h4>
			<div class="row"><span>Никотин</span><b>${nic.addiction ?? 0}</b></div>${bar(nic.percent)}
			<div class="row"><span>Синтетика</span><b>${nar.addiction ?? 0}</b></div>${bar(nar.percent)}
			${row("Ломка (никотин)", WITHDRAWAL[nic.stage ?? 0] ?? "—")}
			${row("Ломка (синтетика)", WITHDRAWAL[nar.stage ?? 0] ?? "—")}
			${row("В крови", `${nic.dose ?? 0} / ${nar.dose ?? 0}`)}
		</div>`);
	}

	if (profile.boozecraft) {
		const b = profile.boozecraft;
		cards.push(`<div class="card booze"><h4>BoozeCraft</h4>
			<div class="row"><span>В крови</span><b>${drunkStage(b.alcoholPercent)}</b></div>${bar(b.alcoholPercent)}
			<div class="row"><span>Зависимость</span><b>${b.addiction ?? 0}</b></div>${bar(b.addictionPercent)}
			${row("Выпито всего", b.drinksTotal ?? 0)}
			${row("Из них алкоголя", b.drinksAlcohol ?? 0)}
			${row("Отключений", b.passOuts ?? 0)}
			${row("Кофеин", b.caffeine ?? 0)}
		</div>`);
	}

	$("profile-cards").innerHTML = cards.join("");
	$("profile-panel").scrollIntoView({ behavior: "smooth", block: "nearest" });
}

async function openProfile(name) {
	try {
		const profile = await api("/api/profile?player=" + encodeURIComponent(name));
		renderProfile(profile);
		history.replaceState(null, "", "?player=" + encodeURIComponent(profile.name));
	} catch (e) {
		$("profile-panel").hidden = false;
		$("profile-name").textContent = name;
		$("profile-sub").textContent = "Игрок не найден — возможно, он ещё не заходил в этом сезоне.";
		$("profile-cards").innerHTML = "";
	}
}

async function refreshStatus() {
	try {
		const status = await api("/api/status");
		$("online").textContent = status.online + " / " + status.max;
		$("tps").textContent = status.tps;
		$("border").textContent = (status.season.border / 1000) + "k";
		const day = status.season.day ? " · день " + status.season.day : "";
		$("season-line").textContent = status.season.name + day;
		$("mods-line").textContent = [
			status.badhabits ? "BadHabits ✓" : "BadHabits —",
			status.boozecraft ? "BoozeCraft ✓" : "BoozeCraft —",
		].join(" · ");

		const list = $("online-list");
		if (!status.players.length) {
			list.innerHTML = '<span class="hint">Никого нет в сети</span>';
		} else {
			list.innerHTML = status.players
				.map((p) => `<span class="chip dot" data-name="${p.name}">${p.name}</span>`)
				.join("");
		}
	} catch (e) {
		$("season-line").textContent = "сервер недоступен";
	}
}

async function refreshTop() {
	try {
		const data = await api("/api/players");
		const body = document.querySelector("#top-table tbody");
		body.innerHTML = data.players.map((p, i) => `<tr data-name="${p.name}">
			<td>${i + 1}</td>
			<td>${p.online ? '<span class="on">●</span> ' : ""}${p.name}</td>
			<td>${hours(p.hours)}</td>
			<td>${p.deaths}</td>
			<td>${p.mobKills}</td>
			<td>${p.playerKills}</td>
		</tr>`).join("");
	} catch (e) {
		/* keep the old table */
	}
}

document.addEventListener("click", (event) => {
	const target = event.target.closest("[data-name]");
	if (target) openProfile(target.dataset.name);
});

$("search-form").addEventListener("submit", (event) => {
	event.preventDefault();
	const value = $("search").value.trim();
	if (value) openProfile(value);
});

$("close-profile").addEventListener("click", () => {
	$("profile-panel").hidden = true;
	history.replaceState(null, "", "/");
});

refreshStatus();
refreshTop();
setInterval(refreshStatus, 10000);
setInterval(refreshTop, 60000);

const deepLink = new URLSearchParams(location.search).get("player");
if (deepLink) openProfile(deepLink);
