import argostranslate.package
import argostranslate.translate


class AgroTranslator:
	"""
	Офлайн переводчик Agros Translate
	"""
	def __init__(self, fromCode, toCode) -> None:
		self.__from_code__ = fromCode
		self.__to_code__ = toCode

		argostranslate.package.update_package_index()
		available_packages = argostranslate.package.get_available_packages()
		package_to_install = next(
			filter(
				lambda x: x.from_code == fromCode and x.to_code == toCode, available_packages
			)
		)
		argostranslate.package.install_from_path(package_to_install.download())


	def translate(self, src):
		"""
		Перевести указанный текст на выбранный ранее язык
		"""
		return argostranslate.translate.translate(src, self.__from_code__, self.__to_code__)