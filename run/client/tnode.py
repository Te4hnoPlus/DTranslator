import traceback


def slitColor(text: str):
	"""
	Разделение текста на части по цветам
	"""
	items = []
	len1 = len(text)
	prev = 0
	i = 0

	while i < len1:
		chr = text[i]
		i += 1

		if(chr == "<"):
			items.append( (0, text[prev:i-1]) )
			for j in range(i, len1):
				chr2 = text[j]

				if(chr2 == ">"):
					items.append( (1, text[i-1:j+1]) )
					prev = j+1
					i = j+1
					break
	items.append( (0, text[prev:len1]) )
	return items


def splitedTranslate(text: str, lim = 1000, translateFunc=lambda a:a):
	"""
	Перевод монолитного текста. 
	Разделение на несколько частей и перевод отдельно, при превышении лимита
	"""
	if len(text) > lim:
		data = []
		pos = 0
		while pos != -1:
			if pos+lim > len(text):
				index = -1
			else:
				index = text.rindex(" ", pos, pos+lim)
			tTextR = text[pos:]
			if index == -1:
				tTextR = text[pos:]
			else:
				tTextR = text[pos:index] 
			tText = translateFunc(tTextR)
			data.append(tText)
			if index == -1:
				break
			pos = index+1
		return " ".join(data)
	else:
		return translateFunc(text)


def translateItems(data: list, lim=1000, translateFunc=lambda a:a):
	"""
	Перевод списка обьектов для перевода, полученных в slitColor
	"""
	translated = []
	for line in data:
		mode, text = line
		if text == None or text == "":
			continue

		if(mode == 0):
			tText = splitedTranslate(text, lim, translateFunc)
			if(tText == None or tText == ""):
				translated.append(text)
			else:
				if text[0] == " " and tText[0] != " ":
					tText = " " + tText

				if text[len(text)-1] == " " and tText[len(tText)-1] != " ":
					tText = tText + " "
				translated.append(tText)
		else:
			translated.append(text)
	
	return "".join(translated)


def translate(src:str, lim=1000, translateFunc=lambda a:a):
	"""
	Перевод конкретного текста со всеми специальными возможностями
	"""
	try:
		return translateItems(slitColor(src), lim, translateFunc)
	except:
		logError()


def logError():
	"""
	Форматирует и печатает ошибку в консоль
	"""
	format = "------------------------------------------------------------------------------------"
	ermsg = "| "+traceback.format_exc().replace("\n", "\n| ")
	print(format+"\n"+ermsg+"\n"+format)