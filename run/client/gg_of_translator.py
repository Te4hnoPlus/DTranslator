from deep_translator import GoogleTranslator
from of_translator import AgroTranslator


class GOffTranslator:
	"""
	Комбинированный переводчик Google и Agros
	В первую очередь будет использовать Google для переводов,
	однако, в случае ошибок переключится на Agros
	"""
	def __init__(self, fromCode, toCode) -> None:
		self.__from_code__ = fromCode
		self.__to_code__ = toCode
		self.delegate: AgroTranslator = None
		self.gtanlator = GoogleTranslator(fromCode, toCode)


	def __init_backup__(self):
		self.delegate = AgroTranslator(self.__from_code__, self.__to_code__)


	def translate(self, src):
		"""
		Перевести указанный текст на выбранный ранее язык
		"""
		if(self.delegate != None):
			return self.delegate.translate(src)
		else:
			try:
				return self.gtanlator.translate(src)
			except:
				print("Disabling Google Translator")
				self.__init_backup__()
				return self.translate(src)