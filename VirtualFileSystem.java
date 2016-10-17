package NetCracker;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Created by Bykova Irina
 * Реализация паттерна MVC (Model-View-Controller)
 * в приложении "Виртуальный файловый менеджер"
 */


public class VirtualFileSystem {

//----------------------------------------------------------
//Модель - Model
//----------------------------------------------------------

    interface VFModelInterface{

        void createTables() throws SQLException;
        void ScanDir(String path1) throws SQLException;
        void clearTables() throws SQLException;
        void MoveFile(String path1, String path2) throws SQLException;
        void CopyFile(String path1, String path2) throws SQLException;
        void RemoveFoD(String path1) throws SQLException;
        void setNumFolder(Integer numFolder);
        void setNumParFolder(Integer numParFolder);
        void setFileID(Integer fileID);
    }

    public static class VFModel implements VFModelInterface{
        private String command;
        VFView view = new VFView(this);

        //Переменные для ScanDir------------------
        Integer numParFolder = 0;    //Считаем PARENT_ID для таблицы Folders в методе ScanDir
        Integer numFolder = 0;       //Считаем ID для таблицы Folders в методе ScanDir
        Integer fileID=0;            //Считаем ID для таблицы Files в методе ScanDir
        Integer pid;                 //Вспомогательная переменная, для numFolder
        Integer pid2;                //Вспомогательная переменная, для numParFolder
        HashMap map = new HashMap(); //Карта для сравнения папок по хэш-значениям для движения по ним.
                                     // Так и не переопределила hashcode, возникли трудности с этим. Поэтому можем
                                     //считывать иерархию папок без повторяющихся имен во вложенных папках
        HashMap map2 = new HashMap(); //Карта для имен родительских папок
        //-----------------------------------------

        public void setNumFolder(Integer numFolder) {
            this.numFolder = numFolder;
        }

        public void setNumParFolder(Integer numParFolder) {
            this.numParFolder = numParFolder;
        }

        public void setFileID(Integer fileID) {
            this.fileID = fileID;
        }

        //Выносим соединение с БД в отдельный метод и вызываем его при необходимости контакта с БД
        private Connection getDBConnection() {
            String dbName = "nc";                           //имя базы данных
            String userName = "rootNC";                     //логин
            String password = "rootNC";                     //пароль
            String url = "jdbc:mysql://localhost:3306/";    //строка подключения
            String driverName = "com.mysql.jdbc.Driver";    //используемый jdbc-драйвер

            Connection dbConnection = null;                 //экземпляр класса Connection для открытия сеанса работы с БД
            Properties prop = new Properties();             //экземпляр класса Properties для задания свойств БД
            prop.setProperty("user",userName);              //устанавливаем свойство - логин
            prop.setProperty("password",password);          //устанавливаем свойство - пароль
            prop.setProperty("useUnicode","true");          //используем юникод
            prop.setProperty("characterEncoding","cp1251"); //и win-1251, чтобы читались русские названия папок и файлов под Windows
                try {
                    Class.forName(driverName);              //пробуем, доступен ли драйвер
                } catch (ClassNotFoundException e) {
                    view.writeLine(e.getMessage());         //печатаем возможные ошибки
                }

                try {
                    dbConnection=DriverManager.getConnection(url+dbName, prop); //создаем подключение
                    return dbConnection;                                        //которое и возвращаем
                } catch (SQLException e) {
                    view.writeLine(e.getMessage());         //печатаем возможные ошибки
                }
                return dbConnection;                        //возвращаем созданное соединение
        }

        //создание таблиц
        public void createTables() throws SQLException {
            Connection dbConnection = null;         //экземпляр класса Connection для открытия сеанса работы с БД
            Statement statement = null;             //экземпляр класса Statement для доступа к БД

            //SQL-запрос на создание таблицы Table с каскадным удалением и обновлением
            String TableFolders = "CREATE TABLE IF NOT EXISTS FOLDERS(" +
                    "id integer NOT NULL, " +
                    "parent_id integer," +
                    "fname varchar(255) NOT NULL," +
                    "PRIMARY KEY(id)," +
                    "FOREIGN KEY(parent_id) REFERENCES FOLDERS(id)" +
                    "ON DELETE CASCADE ON UPDATE CASCADE" +
                    ") ";

            //SQL-запрос на создание таблицы Files с каскадным удалением и обновлением
            String TableFiles =
                    "CREATE TABLE IF NOT EXISTS FILES(" +
                            "id integer NOT NULL," +
                            "fname varchar(255) NOT NULL," +
                            "folder_id integer," +
                            "PRIMARY KEY(id)," +
                            "FOREIGN KEY(folder_id) REFERENCES FOLDERS(id) " +
                            "ON DELETE CASCADE ON UPDATE CASCADE" +
                            ") ";
            try {
                dbConnection = getDBConnection();               //подключаемся к базе
                statement = dbConnection.createStatement();     //создаем объект, связанный с сеансом Connection

                // выполнить SQL запрос
                statement.executeUpdate(TableFolders);          //создаем таблицу Tables
                statement.executeUpdate(TableFiles);            //создаем таблицу Files

            } catch (SQLException e) {//если возникнут ошибки при работе с базой, также выведем их, через Представление
                view.writeLine(e.getMessage());
            } finally {
                if (statement != null) {        //если statement был открыт, то закрываем
                    statement.close();
                }
                if (dbConnection != null) {     //если соединение было открыто - закрываем
                    dbConnection.close();
                }
            }
        }

        //Очистка таблиц
        public void clearTables() throws SQLException{
            Connection dbConnection = null;             //экземпляр класса Connection для открытия сеанса работы с БД
            Statement statement = null;                 //экземпляр класса Statement для доступа к БД
            String TableFolders =
                    "DELETE FROM FOLDERS;";             //Заготовка для очистки таблицы Folders
            String TableFiles =
                    "DELETE FROM FILES";                //Заготовка для очистки таблицы Files

            try {
                dbConnection = getDBConnection();       //подключаемся к базе
                statement = dbConnection.createStatement();  //создаем объект, связанный с сеансом Connection

                // выполнить SQL запрос
                statement.executeUpdate(TableFolders); //на очистку таблицы Folders
                statement.executeUpdate(TableFiles);   //на очистку таблицы Files
            } catch (SQLException e) {
                view.writeLine(e.getMessage()); //если возникнут ошибки при работе с базой, также выведем их, через Представление
            } finally {
                if (statement != null) {        //если statement был открыт, то закрываем
                    statement.close();
                }
                if (dbConnection != null) {     //если соединение было открыто - закрываем
                    dbConnection.close();
                }
            }
        }


        //Сканирование директории тоже отнесла к работе с моделью, так как сразу при считывании заносим результаты в БД
        public void ScanDir(String path1) throws SQLException{
            Connection dbConnection = null;         //экземпляр класса Connection для открытия сеанса работы с БД
            Statement statement = null;             //экземпляр класса Statement для доступа к БД
            File file = new File(path1);            //В метод передается путь к сканируемому файлу
            File[] listofFiles = file.listFiles();  //Создаем список файлов для движения вниз по папкам
            Integer count = listofFiles.length;     //при каждом заходе в папку считаем количество файлов

            try {
                dbConnection = getDBConnection();   //подключаемся к базе
                statement = dbConnection.createStatement();  //создаем объект, связанный с сеансом Connection

                //если нет такого пути, то печатаем исключение
                if (!file.exists()){
                    throw new IllegalArgumentException("Такого каталога не существует");
                }
                else {
                    //Первую запись вставляем с Parent_id = null, и ID = 0
                    if (numFolder==0) {
                        statement.executeUpdate("INSERT INTO FOLDERS VALUES(" + numFolder + ", null ,\"" + file.getName() + "\");");
                        //на всякий случай
                        statement.executeUpdate("COMMIT;");
                    }
                    //Далее сканируем содержимое папки
                    for (int i = 0; i < count; i++) {
                        //может встретиться как папка, так и файл, если это папка, идем дальше
                        if (listofFiles[i].isDirectory()) {
                            //Извлекаем ключ из карты, куда складываем наименования папок
                            pid = (Integer)map.get(listofFiles[i].getName());
                            //Извлекаем ключ из карты, куда складываем наименования родительских папок
                            pid2 = (Integer)map2.get(listofFiles[i].getParent());
                            //Если названия этой папки еще не было, то увеличиваем счетчик и сохраняем название папки и идентификатор
                            if (pid==null){
                                pid = new Integer(++numFolder);
                                map.put(listofFiles[i].getName(),pid);
                            }
                            //Если названия родительской папки еще не было, также сохраняем названия папки, ID и
                            //увеличиваем счетчик
                            if (pid2==null) {
                                pid2 = new Integer(numParFolder);
                                map2.put(listofFiles[i].getParent(),pid2);
                                ++numParFolder;
                            }
                            //Вставляем в таблицу FOLDERS следующую запись
                            statement.executeUpdate("INSERT INTO FOLDERS VALUES(" + pid + "," + pid2 + ", \"" + listofFiles[i].getName() + "\" );");
                            //Рекурсия, для обхода всех папок
                            ScanDir(listofFiles[i].getPath());
                        }
                        //если папки в этой папке закончились, заносим запись о файлах в этой папке
                        else {
                            try {
                                //Родительская папка может встретиться и при обходе файлов
                                pid2 = (Integer)map2.get(listofFiles[i].getParent());
                                if (pid2==null) {
                                    //Если названия родительской папки еще не было, также сохраняем названия папки, ID и
                                    //увеличиваем счетчик
                                    pid2 = new Integer(numParFolder);
                                    map2.put(listofFiles[i].getParent(),pid2);
                                    ++numParFolder;
                                }
                                //Вставляем в таблицу FILES запись с информацией о родительской папке
                                statement.executeUpdate("INSERT INTO FILES VALUES(" + fileID + ", \"" + listofFiles[i].getName() + "\"," + pid2 + ");");
                                ++fileID;

                            }
                            //может возникнуть такое исключение, посмотрим стек
                            catch(NullPointerException es){
                                es.printStackTrace();
                            }
                        }
                    }
                }

            } catch (SQLException e) { //если возникнут ошибки при работе с базой, также выведем их, через Представление
                view.writeLine(e.getMessage());
            } finally {

                if (statement != null) {    //если statement был открыт, то закрываем
                    statement.close();
                }
                if (dbConnection != null) { //если соединение было открыто - закрываем
                    dbConnection.close();
                }
            }
        }

        //Метод для проверки правильности указания путей расположения файлов.
        // Возвращает идентификаторы: ID из FOLDERS
        // ID, FOLDER_ID из FILES
        private HashMap<String,String> CheckPath(String path, int k) throws SQLException{
            Connection dbConnection = null; //экземпляр класса Connection для открытия сеанса работы с БД
            Statement statement = null;     //экземпляр класса statement для доступа к БД
            ResultSet rs;                   //экземпляр класса ResultSet для получения результата запроса

            //разбиваем переданную строку на токены - избавляемся от слэш
            StringTokenizer st = new StringTokenizer(path,"/");
            //Используем "карту" для хранения названий вложенных папок
            Map<Integer,String> map = new HashMap<Integer,String>();
            //Используем вторую "карту" для возвращения некоторых значений, полученных из результатов запросов
            HashMap<String, String> resultMap = new HashMap<String, String>();

            boolean b1 = true;  //флаг, для проверки правильности пути к папке
            boolean b2 = true;  //флаг, для проверки правильности пути к файлу
            int id_fol=-1;      //в переменной храним идентификатор папки - из запроса
            int id_file = -1;   //в переменной храним идентификатор родительской папки - из запроса
            int id = -1;        //в переменной храним идентификатор файла - из запроса

            try {
                dbConnection = getDBConnection();                   //подключаемся к базе
                statement = dbConnection.createStatement();         //создаем объект, связанный с сеансом Connection
                int count = 0;                                      //инициализируем ключ для "карты" названий папок
                int spec = 1; //переменная используется, чтобы в карте не считывался последний элемент - название файла
                              //т.к. при перемещении и копировании мы передаем путь с названием файла в конце

                //Строку, разбитую на токены, складываем в карту
                while (st.hasMoreTokens()) {
                    map.put(count, st.nextToken());
                    count++;
                }
                //передаем Представлению строку для вывода на экран
                view.writeLine("Начинаю проверку " + path+"\n");

                //Если передан параметр -1, значит пришла строка из rm, где надо проверить на валидность путь к папке
                if (k==-1){
                    //поэтому будем сканировать до последнего токена
                    spec = 0;
                }

                //Пробегаем по каждому названию файла или папки
                for (Map.Entry<Integer, String> pair: map.entrySet()) {
                    //сначала проверяем папки
                    if (!(pair.getKey() == map.size() - spec)) {
                        //извлекаем ID по названию папки из таблицы Folders.
                        // Баг со считыванием папок с одинаковым именем пока не решен, поэтому считаем имя уникальным
                        rs = statement.executeQuery("SELECT ID, FNAME FROM FOLDERS WHERE ( FNAME = \"" + pair.getValue() + "\")");
                        //Из полученного результата сохраняем ID папки
                        while (rs.next()) {
                            id_fol = rs.getInt("ID");
                            //Сохраняем ID как строку, потому-что в значения resultMap нужно будет складывать строковые
                            // значения - например название результирующего файла. Поэтому и все ID тоже делаем строкой
                            resultMap.put("FOLDER_ID", Integer.toString(id_fol));
                        }
                        //проверка на правильность пути, если нет результир. набора, значит такой путь не существует
                        //или введен неверно
                        if (!rs.first()) {
                            b1 = false;
                        }
                    } else {
                        if (k == 0) { //проверяем файл
                            //Смотрим, есть ли в таблице FILES файл с таким ID папки и таким именем
                            rs = statement.executeQuery("SELECT ID, FOLDER_ID, FNAME FROM FILES WHERE ((FOLDER_ID " +
                                    "= " + id_fol + ") AND (FNAME = \"" + pair.getValue() + "\"))");
                            //смотрим результат
                            while (rs.next()) {
                               //сохраняем ID папки-родителя
                                id_file = rs.getInt("folder_id");
                                //сохраняем ID файла
                                id = rs.getInt("id");
                                //и кладем в результирующий map
                                resultMap.put("PARENT_FOLDER_ID", Integer.toString(id_file));
                                resultMap.put("ID", Integer.toString(id));
                            }
                            //соотвественно, если набор пуст, то такого файла нет
                            if (!rs.first()) {
                                b2 = false;
                            }
                        } else {
                            //если пришел k=1, значит такого файла нет, но он должен быть создан при копировании или
                            //перемещении. Просто сохраняем его имя для послед. использования в запросе.
                            if (k == 1) {
                                resultMap.put("FNAME_REZ", pair.getValue());
                            }
                        }
                    }
                }
                //Отлавливаем неверные пути
                if ((b1==false)) {
                   view.writeLine("Проверьте путь к исходному файлу. Такого пути не существует.\n");
                } else if (b2==false){
                    view.writeLine("Проверьте расположение исходного файла. Такого файла нет в указанной папке\n");
                }
                else {
                    view.writeLine("Расположение "+path+" задано верно\n");
                }
            }
            catch (SQLException e) {
                //если возникнет исключение, связанное с БД, то выведем на экран, используя команду writeLine "Представления"
                view.writeLine(e.getMessage());
            } finally {
                if (statement != null) {        //если statement был открыт, то закрываем
                    statement.close();
                }
                if (dbConnection != null) {     //если соединение было открыто - закрываем
                    dbConnection.close();
                }
            }
            return resultMap;               //возвращаем карту со значениями ID файла, ID папки, PARENT_ID файла, FNAME
        }

        //Виртуальное перемещение файла
        public void MoveFile(String path1, String path2) throws SQLException {
            Connection dbConnection = null; //экземпляр класса Connection для открытия сеанса работы с БД
            Statement statement = null;     //экземпляр класса statement для доступа к БД
            try {
                dbConnection = getDBConnection();           //подключаемся к базе
                statement = dbConnection.createStatement(); //создаем объект, связанный с сеансом Connection
                //проверяем первый путь на валидность. Второй параметр (0) в CheckPath сообщает, что нужно проверять как файл
                HashMap<String, String> res1 = CheckPath(path1,0);
                //проверяем второй путь на валидность. Второй параметр (1) в CheckPath сообщает, что нужно проверять
                //на валидность только папки, а название результирующего файла запомнить
                HashMap<String, String> res2 = CheckPath(path2,1);
                //после проверки второго пути, извлекаем ID конечной папки и название файла
                int id2 = Integer.parseInt(res2.get("FOLDER_ID"));
                String fname = res2.get("FNAME_REZ");
                //после проверки первого пути извлекаем ID файла-источника
                int id3 = Integer.parseInt(res1.get("ID"));
                //Перемещение файла - обновление текущей записи значениями конечной папки и новым значением имени
                statement.executeUpdate("UPDATE FILES SET FOLDER_ID = "+id2+", FNAME = \""+fname+ "\" WHERE ID = " + id3);
                //передаем в Представление строку вывода
                view.writeLine("Файл успешно перемещен в "+path2+"\n");
            }
            catch (SQLException e) {
                //если возникнет исключение, связанное с БД, то выведем на экран, используя команду writeLine "Представления"
                view.writeLine(e.getMessage());
            } finally {
                if (statement != null) {    //если statement был открыт, то закрываем
                    statement.close();
                }
                if (dbConnection != null) { //если соединение было открыто - закрываем
                    dbConnection.close();
                }
            }
        }

        //Виртуальное копирование файла
        public void CopyFile(String path1, String path2) throws SQLException, NumberFormatException{
            Connection dbConnection = null; //экземпляр класса Connection для открытия сеанса работы с БД
            Statement statement = null;     //экземпляр класса statement для доступа к БД
            ResultSet rs;                   //экземпляр класса ResultSet для получения результата запроса
            try {
                dbConnection = getDBConnection();               //подключаемся к базе
                statement = dbConnection.createStatement();     //создаем объект, связанный с сеансом Connection
                //проверяем первый путь на валидность. Второй параметр (0) в CheckPath сообщает, что нужно проверять как файл
                HashMap<String, String> res1 = CheckPath(path1,0);

                //проверяем второй путь на валидность. Второй параметр (1) в CheckPath сообщает, что нужно проверять
                //на валидность только папки, а название результирующего файла запомнить
                HashMap<String, String> res2 = CheckPath(path2,1);
                //после проверки второго пути, извлекаем ID конечной папки
                int id2 = Integer.parseInt(res2.get("FOLDER_ID"));
                //переменная для хранения общего числа записей в БД
                int num = 0;
                //запрашиваем общее число записей из БД
                rs = statement.executeQuery("SELECT COUNT(*) AS NUM FROM FILES");
                //результат - одна строка, но по другому не получилось прочитать результаты запроса
                while (rs.next()) {
                    //сохраняем число записей
                    num = rs.getInt("NUM");
                }

                //после проверки второго пути, извлекаем имя конечной папки
                String fname = res2.get("FNAME_REZ");
                //создаем в таблице FILES еще одну запись с порядковым номером - num, переданным именем и ID конечной папки
                statement.executeUpdate("INSERT INTO FILES VALUES ("+num+",\""+fname+"\","+id2+");");
                view.writeLine("Файл успешно скопирован в "+path2+"\n");
            }
            catch (SQLException e) {
                //если возникнет исключение, связанное с БД, то выведем на экран, используя команду writeLine "Представления"
                view.writeLine(e.getMessage());
            } finally {
                if (statement != null) {  //если statement был открыт, то закрываем, ResultSet закроется вместе с ним
                    statement.close();
                }
                if (dbConnection != null) { //если соединение было открыто - закрываем
                    dbConnection.close();
                }
            }
        }

        //Виртуальное удаление файла или папки
        public void RemoveFoD(String path1) throws SQLException{
            Connection dbConnection = null; //экземпляр класса Connection для открытия сеанса работы с БД
            Statement statement = null;     //экземпляр класса statement для доступа к БД
            int id;                         //переменная для хранения идентификатора файла или папки для послед. удаления

            try {
                dbConnection = getDBConnection();                    //подключаемся к базе
                statement = dbConnection.createStatement();          //создаем объект, связанный с сеансом Connection
                StringTokenizer st = new StringTokenizer(path1,"/"); //разбиваем переданную строку на токены - избавляемся от слэш
                String lastToken="";                                 //переменная для хранения последнего токена в строке
                String flag;                                         //флаг, для определения файл это или папка

                while (st.hasMoreTokens()) {                         //пока есть аргументы в строке
                    lastToken = st.nextToken();                      //добираемся до последнего аргумента
                }

                //чтобы определить, папка это или файл - найдем в последнем переданном токене точку
                //определение примитивно, если файл без расширения - будет считать его папкой
                //некоторые папки могут содержать в начале точку, игнорируем такие
                if ((lastToken.contains("."))&&(lastToken.indexOf(".")!=0)) {
                    flag = "file";                  //если точка есть, и она не в начале, то это файл
                } else flag = "folder";             //иначе это папка

                //если строка - это папка
                if (flag.equals("folder")) {
                    //проверяем путь к ней на валидность. Второй параметр (-1) в CheckPath сообщает, что нужно проверять как папку
                    HashMap<String, String> res1 = CheckPath(path1,-1);
                    //запоминаем идентификатор папки, которую нужно удалить
                    id = Integer.parseInt(res1.get("FOLDER_ID"));
                    //удаляем из базы папку и все вложенные файлы и папки - за счет cascade delete
                    statement.executeUpdate("DELETE FROM FOLDERS WHERE ID = " + id + ";");
                    //передаем Представлению строку для вывода на экран
                    view.writeLine("Папка " + path1 + " удалена\n");
                } else if (flag.equals("file")) { //если строка - это файл
                    //проверяем путь к ней на валидность. Второй параметр (0) в CheckPath сообщает, что нужно проверять как файл
                    HashMap<String, String> res1 = CheckPath(path1,0);
                    //запоминаем идентификатор файла, который нужно удалить
                    id = Integer.parseInt(res1.get("ID"));
                    //удаляем из базы файл
                    statement.executeUpdate("DELETE FROM FILES WHERE ID = " + id + ";");
                    //передаем Представлению строку для вывода на экран
                    view.writeLine("Файл " + path1 + " удален \n");
                }
            }
            catch (SQLException e) {
                //если возникнет исключение, связанное с БД, то выведем на экран, используя команду writeLine "Представления"
                view.writeLine(e.getMessage());
            } finally {
                if (statement != null) { //если statement был открыт, то закрываем
                    statement.close();
                }
                if (dbConnection != null) { //если соединение было открыто - закрываем
                    dbConnection.close();
                }
            }
        }
    }

    //-----------------------------------------------------
    //Представление - View
    //-----------------------------------------------------

    public static class VFView {
        VFModelInterface model;                     //экземпляр интерфейса модели
        VFControllerInterface controller;           //экземпляр интерфейса контроллера

        //Конструктор класса VFView
        public VFView(VFControllerInterface controller, VFModelInterface model){
            this.controller = controller;           //передаем в класс "Представление" ссылку на передаваемый экземпляр контроллера
            this.model = model;                     //передаем в класс "Представление" ссылку на передаваемый экземпляр модели
        }

        public VFView(VFModelInterface model){
            this.model = model;
        }

        //Метод позволяет выводить любой символ
        void writeLine(String ch){
            System.out.print(ch);                  //выводим строку или символ, без перевода каретки
        }

        //Считываем введенную строку
        String readLine(){
            Scanner sc = new Scanner(System.in); //обращаемся к экземпляру класса Scanner для ввода данных
            String vector = sc.nextLine();       //считываем следующую строку
            return vector;                       //возвращаем считанную строку
        }

    }

    //--------------------------------------------------------
    //Контроллер - Controller
    //--------------------------------------------------------

    public interface VFControllerInterface{
        void getLine(VFView view) throws SQLException;
        void setKindCommand(String command, String path1, String path2) throws SQLException;
    }

    //Класс контроллер, расширяет интерфейс VFControllerInterface
    public static class VFController implements VFControllerInterface {
        VFModelInterface model;                 //экземпляр интерфейса модели
        VFView view;                            //экземпляр "Представления"

        //конструктор контроллера, параметр - интерфейс модели
        public VFController(VFModelInterface model) throws SQLException{
            this.model = model;                 //передаем в класс контроллер ссылку на передаваемый экземпляр модели
            view = new VFView(this,model);      //создание нового "Представления"
            model.createTables();              //обращаемся к модели, создаем таблицы
            //Краткий help по использованию
            view.writeLine("----------------------------Virtual File System------------------------------\n"+
                    "scan #path – сканирует путь и загружает содержимое папки в базу. Пример scan C:\\Data \n\n" +
                    "mv #filename #filename2 – перемещает виртуально файл из одной директории в другую.\n" +
                    "Пример mv /Data/file.txt /Data/folder2/file2.txt \n\n" +
                    "cp #filename #filename2 – копирует виртуально файл из одной директории в другую.\n" +
                    "Пример cp /Data/folder2/file2.txt /Data/file.txt  \n\n" +
                    "rm #filename - удаляет файл. Пример rm /Data/file.txt\n" +
                    "rm #foldername - удаляет папку со всем содержимым. Пример rm /Data\n"+
                    "----------------------------------------------------------------------------\n"+
                    "Старайтесь не использовать пробелы в именах файлов и папок. Возможна некорректная работа программы!\n"+
                    "Для выхода из программы введите end\n"+
                    "----------------------------------------------------------------------------\n");
            view.writeLine(">");               //обращаемся к представлению, печатаем символ приглашения к вводу
            getLine(view);                     //обращаемся к представлению, считываем строку

        }

        //метод для считывания строки, разбиения ее на значимые элементы - токены и передачи элементов для дальнейшей
        //обработки в метод setKindCommand
        //Параметр - экземпляр "Представления"
        public void getLine(VFView view) throws SQLException{
            String command;   //переменная для хранения введенной команды на
            String path1="";  //переменная для хранения первого пути к файлам или записям
            String path2="";  //переменная для хранения второго пути к файлам или записям

            String LineCom = view.readLine(); //обращаемся к представлению, считываем строку
            while (!LineCom.equals("end")) { //для прекращения считывания нужно указать ключевое слово "end"
                StringTokenizer st = new StringTokenizer(LineCom," "); //введенную строку разбиваем на токены, чтобы
                                                                       //отделить команду от путей к файлам и папкам
                ArrayList<String> arr = new ArrayList<String>();       //в коллекцию будем складывать токены для
                                                                       //дальнейшего анализа
                while (st.hasMoreTokens()) {                           //проходим по массиву токенов, добавляем в
                    arr.add(st.nextToken());                           //коллекцию arr
                }
                command = arr.get(0);      //первый элемент должен быть командой

                if (arr.size()>=3) {       //проверка, введены два или более аргументов
                    path1 = arr.get(1);    //введен и первый путь
                    path2 = arr.get(2);    //и второй путь
                } else if (arr.size()>=2){ //проверка, если введен хотя бы один аргумент
                    path1 = arr.get(1);    //если размер массива два и более, то введена команда и один путь
                } else path2 = null;       //иначе считаем, что второй путь не введен (не предусмотрено командой)
                setKindCommand(command, path1, path2); //передаем введенную команду и пути методу, которая распознает
                                                       // вид команды и направит соответствующему методу в модели
                view.writeLine(">");                   //выводим знак приглашения к вводу
                LineCom = view.readLine();             //обращаемся к представлению для дальнейшего считывания строки
            }
        }

        //метод обрабатывает передаваемые параметры из введенной строки: команду, пути к файлам и папкам или к записям в БД
        public void setKindCommand(String command, String path1, String path2) throws SQLException{
            if (command.equals("scan")) { //если введена команда сканировать, переходим
                model.clearTables();      //к очищению созданных таблиц
                model.ScanDir(path1);     //к сканированию папки
                view.writeLine("Путь просканирован. Структура каталогов и файлов занесена в базу\n");

                //обнуляем счетчики папок и файлов в БД, чтобы второй раз за сеанс можно было просканировать папку
                model.setNumFolder(0);
                model.setNumParFolder(0);
                model.setFileID(0);

            }else if (command.equals("mv")){ //если задана команда перемещения, переходим к методу перемещения
                model.MoveFile(path1,path2);
            } else if (command.equals("cp")){ //если задана команда копирования, переходим к методу копирования
                model.CopyFile(path1,path2);
            } else if (command.equals("rm")){ //если задана команда удаления, переходим к методу удаления
                model.RemoveFoD(path1);
            } else {                         //обрабатываем вероятность неверного ввода команды
                view.writeLine("Введена неизвестная команда, проверьте правильность ввода!");
                System.out.println();
            }
        }
    }


    //Запуск программы

    public static void main(String[] args) throws IOException, SQLException {
        VFModelInterface model = new VFModel();
        VFControllerInterface controller = new VFController(model);
    }
}
