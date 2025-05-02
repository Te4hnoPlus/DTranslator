import socket, sys, time
from tnode import translate
from of_translator import AgroTranslator
import json


def connectInfo():
	"""
	Считать параметры подключения из аргументов запуска
	"""
	try:
		raw = sys.argv[1].split(":")
		return (raw[0], int(raw[1]))
	except:
		return ("localhost", 23700)
	

def maybeUseGoogle():
	"""
	Проверить, стоит ли использовать внешний переводчик (Google Translate)
	"""
	try:
		raw = sys.argv[1].split(":")
		return len(raw) > 2 and raw[2] == "gg"
	except:
		return False


def startClient(host, port, maybegoogle=maybeUseGoogle()):
	"""
	Запустить клиент децентрализованного перевода
	"""
	print(f"Connecting to {host}:{port}")

	attempts = 3

	while True:
		if attempts < 0:
			time.sleep(0.75)
		else:
			attempts -= 1
		try:
			clientsocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
			clientsocket.connect((host, port))
		except:
			continue
		defined = False
		translateFunc = None

		while True:
			try:
				data = clientsocket.recv(1024*5)
			except Exception:
				break
			if(len(data) == 0):
				break
			str1 = str(data, "utf-8")
			if not defined:
				data = str1.split(":")
				print(f"Connection success, lang: {data[0]}->{data[1]}")
				print("Loading translation packet ...")

				translator = None
				if maybegoogle:
					try:
						from gg_of_translator import GOffTranslator
						translator = GOffTranslator(data[0], data[1])
					except:
						pass
				if translator == None:
					translator = AgroTranslator(data[0], data[1])

				translateFunc = lambda a: translator.translate(a)
				defined = True
				try:
					clientsocket.sendall("ready".encode('utf-8'))
					print("Success. Ready to work.")
					continue
				except Exception:
					break

			result = translate(str1, 1000, translateFunc)

			if result == "" or result == None:
				result = str1

			try:
				clientsocket.sendall(json.dumps({"k": str1, "v": result}).encode('utf-8'))
			except Exception:
				break
		
		print("Try to reconnect ...")


if __name__ == "__main__":
	host, port = connectInfo()
	startClient(host, port)
	print("Stopped.")